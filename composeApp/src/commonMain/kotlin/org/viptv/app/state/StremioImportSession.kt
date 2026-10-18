package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*

enum class StremioImportStage(val step: Int, val title: String) {
    Connect(1, "Connect Stremio"), Addons(2, "Choose add-ons"), Items(3, "Review items"), Confirm(4, "Confirm import"), Complete(0, "Import complete")
}
enum class StremioImportBusy(val label: String) {
    Preview("Reading Stremio for a read-only preview…"), Review("Verifying metadata and preparing named items…"), Apply("Saving the confirmed import…")
}

/** Never holds the Stremio password: the field owns it until [StremioImportSession.connect] drops it. */
data class StremioImportState(
    val profileName: String,
    val restricted: Boolean,
    /** Browser account sessions choose add-ons; native device sessions only see them. */
    val addonsEditable: Boolean,
    val stage: StremioImportStage = StremioImportStage.Connect,
    val email: String = "",
    val library: Boolean = true,
    val progress: Boolean = true,
    val addons: StremioAddonPreview? = null,
    val selectedAddons: Set<String> = emptySet(),
    val failedAddons: Set<String> = emptySet(),
    val review: StremioReview? = null,
    val excluded: Set<String> = emptySet(),
    val reviewedAddons: String? = null,
    val completion: StremioCompletion? = null,
    val confirmed: Boolean = false,
    val selectionLocked: Boolean = false,
    val expired: Boolean = false,
    val busy: StremioImportBusy? = null,
    val error: String? = null,
    /** Increments when the draft is discarded so the form clears its fields and restores email focus. */
    val resets: Int = 0,
) {
    val connected: Boolean get() = addons != null || review != null
    val chosenCounts: Map<String, Int>? get() = review?.let { stremioSelectedCounts(it.summary, it.items, excluded) }
    val included: Int get() = review?.items?.count { it.selectable && it.id !in excluded } ?: 0
    val hasScope: Boolean get() = review != null && (review.addonsToAdd > 0 ||
        chosenCounts.orEmpty().let { counts -> listOf("favorites_to_add", "progress_to_add", "progress_to_update").any { (counts[it] ?: 0) > 0 } })
}

internal fun stremioSelectedCounts(summary: Map<String, Int>, items: List<StremioReviewItem>, excluded: Set<String>): Map<String, Int> {
    val result = summary.toMutableMap()
    for (item in items) if (item.id in excluded) for (key in stremioRowKeys)
        result[key] = maxOf(0, (result[key] ?: 0) - (item.counts[key] ?: 0))
    return result
}

private val staleCodes = setOf("stremio_preview_stale", "stremio_preview_expired", "stremio_preview_not_found", "stremio_selection_mismatch")
internal const val stremioStaleCopy = "This preview is no longer current. Preview again."
internal const val stremioUncertainApplyCopy = "Could not confirm the import. Retry this preview; completed items will not be duplicated."

/** Fixed copy only; upstream diagnostics are never shown. */
internal fun stremioImportError(failure: Throwable, applying: Boolean): String {
    if (failure is StremioImportFailure) when (failure.code) {
        "stremio_credentials_invalid" -> return "Stremio sign-in failed. Check your details."
        in staleCodes -> return stremioStaleCopy
        "stremio_restricted_profile" -> return "Switch to an unrestricted profile to import."
        "stremio_source_unavailable" -> return "Could not read Stremio. Try again."
        "stremio_addon_unavailable" -> return "An add-on could not be verified. Change your add-on choices and try again."
        "stremio_import_busy" -> return "Too many imports are pending. Cancel a preview or try again shortly."
        "stremio_storage_unavailable" -> return "Import storage is temporarily unavailable. Try again."
        "stremio_invalid_request" -> return "This import request could not be accepted. Start a new preview and try again."
        "secret_store_not_configured" -> return "The server needs encrypted add-on storage before importing configuration."
        "account_session_required" -> return "Add-ons can only be added from VIPTV on the web."
    }
    if (failure is StremioInvalidResponse && failure.message in setOf("Invalid review", "Invalid add-ons", "Replaced review"))
        return "The server returned an unexpected import review. Start a new preview or update VIPTV."
    return if (applying) stremioUncertainApplyCopy else "Could not prepare the import. Check your details or add-on choices and try again."
}

/**
 * One guided import draft for one connection and profile. Every request bumps
 * [epoch]; responses from an older epoch, a disposed draft or a replaced owner
 * are ignored. Discarding cancels the request and deletes the server preview.
 */
class StremioImportSession internal constructor(
    private val scope: CoroutineScope,
    private val transport: StremioImportTransport,
    val profileId: String,
    profileName: String,
    restricted: Boolean,
    addonsEditable: Boolean,
    private val now: () -> Long = ::epochMillis,
    internal val owned: () -> Boolean = { true },
) {
    private val mutable = MutableStateFlow(StremioImportState(profileName, restricted, addonsEditable))
    val state = mutable.asStateFlow()
    private val current get() = mutable.value
    private var epoch = 0L
    private var pending: Job? = null
    private var timer: Job? = null
    private var handle: String? = null
    private var disposed = false

    private fun usable() = !disposed && !current.restricted && owned()
    private fun live(request: Long) = request == epoch && !disposed && owned()
    private fun idle() = pending == null && current.busy == null
    private fun start(request: Long, block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { block() } finally { if (request == epoch) { pending = null; mutable.value = current.copy(busy = null) } }
        }
        pending = job; job.start()
    }
    private fun expiredAt(preview: StremioPreview) = preview.expiresAt * 1000 <= now()

    fun setLibrary(value: Boolean) { if (idle() && !current.connected) mutable.value = current.copy(library = value) }
    fun setProgress(value: Boolean) { if (idle() && !current.connected) mutable.value = current.copy(progress = value) }

    /** The password is only captured by this request; it is never stored in state. */
    fun connect(email: String, password: String) {
        val draft = current
        if (!idle() || !usable() || draft.connected || draft.completion != null || email.isBlank() || password.isEmpty()) return
        val request = ++epoch
        val address = email.trim()
        mutable.value = draft.copy(error = null, busy = StremioImportBusy.Preview)
        start(request) { loadPreview(request, address, password, draft.library, draft.progress) }
    }

    private suspend fun loadPreview(request: Long, email: String, password: String, library: Boolean, progress: Boolean) {
        try {
            val value = transport.preview(profileId, email, password, library, progress)
            if (!live(request)) return
            when (value) {
                is StremioAddonPreview -> {
                    handle = value.previewId
                    mutable.value = current.copy(email = email, addons = value, expired = false, stage = StremioImportStage.Addons,
                        selectedAddons = if (current.addonsEditable) value.addons.filter { it.status != "unavailable" }.map { it.id }.toSet() else emptySet())
                    scheduleExpiry()
                }
                is StremioReview -> { mutable.value = current.copy(email = email); acceptReview(value, null) }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (live(request)) mutable.value = current.copy(error = stremioImportError(error, false)) }
    }

    private fun acceptReview(value: StremioReview, chosen: String?) {
        handle = value.previewId
        val previous = current.excluded
        mutable.value = current.copy(review = value, reviewedAddons = chosen, confirmed = false, expired = false,
            selectionLocked = false, stage = StremioImportStage.Items,
            excluded = value.items.filter { it.selectable && it.id in previous }.map { it.id }.toSet())
        scheduleExpiry()
    }

    private fun canChooseAddons() = current.addonsEditable && idle() && !current.expired && current.stage == StremioImportStage.Addons
    fun toggleAddon(id: String, selected: Boolean) {
        val addon = current.addons?.addons?.find { it.id == id } ?: return
        if (!canChooseAddons() || (selected && addon.status == "unavailable")) return
        mutable.value = current.copy(confirmed = false, selectedAddons = if (selected) current.selectedAddons + id else current.selectedAddons - id)
    }
    fun selectAllAddons() {
        val addons = current.addons ?: return
        if (canChooseAddons()) mutable.value = current.copy(confirmed = false,
            selectedAddons = addons.addons.filter { it.status != "unavailable" }.map { it.id }.toSet())
    }
    fun clearAddons() { if (canChooseAddons()) mutable.value = current.copy(confirmed = false, selectedAddons = emptySet()) }
    fun continueWithoutAddons() {
        if (!canChooseAddons()) return
        mutable.value = current.copy(selectedAddons = emptySet(), confirmed = false)
        verify(emptySet())
    }
    /** Native device sessions may not register add-ons, so they always review with none selected. */
    fun reviewItems() = verify(if (current.addonsEditable) current.selectedAddons else emptySet())

    private fun verify(chosen: Set<String>) {
        val draft = current
        val addons = draft.addons ?: return
        if (draft.expired || !idle() || !usable() || expiredAt(addons)) return
        val key = chosen.sorted().joinToString(",")
        if (draft.review != null && draft.reviewedAddons == key) {
            mutable.value = draft.copy(error = null, stage = StremioImportStage.Items); return
        }
        val request = ++epoch
        mutable.value = draft.copy(reviewedAddons = null, error = null, failedAddons = emptySet(), busy = StremioImportBusy.Review)
        start(request) { loadReview(request, addons, chosen.sorted(), key, draft.review?.revision ?: 0) }
    }

    private suspend fun loadReview(request: Long, addons: StremioAddonPreview, chosen: List<String>, key: String, revision: Long) {
        try {
            val value = transport.review(profileId, addons.previewId, chosen, revision)
            if (!live(request)) return
            val prior = current.review
            if (value.previewId != addons.previewId || (prior != null && value.revision <= prior.revision))
                throw StremioInvalidResponse("Replaced review")
            acceptReview(value, key)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!live(request)) return
            mutable.value = current.copy(reviewedAddons = null, confirmed = false)
            if (error is StremioImportFailure && error.code == "stremio_addon_unavailable")
                mutable.value = current.copy(failedAddons = addons.addons.map { it.id }.filter { it in error.failedAddons }.toSet())
            if (error is StremioImportFailure && error.code in staleCodes) { reset(); mutable.value = current.copy(expired = true) }
            mutable.value = current.copy(error = stremioImportError(error, false))
        }
    }

    private fun canChooseItems() = idle() && !current.expired && !current.selectionLocked && current.review != null
    fun setItemsIncluded(ids: Collection<String>, included: Boolean) {
        val review = current.review ?: return
        if (!canChooseItems()) return
        val selectable = review.items.filter { it.selectable && it.id in ids }.map { it.id }
        mutable.value = current.copy(confirmed = false, excluded = if (included) current.excluded - selectable.toSet() else current.excluded + selectable)
    }

    fun toConfirm() {
        val draft = current
        if (draft.stage == StremioImportStage.Items && idle() && !draft.expired && draft.hasScope)
            mutable.value = draft.copy(confirmed = false, stage = StremioImportStage.Confirm)
    }
    fun setConfirmed(value: Boolean) {
        val draft = current
        if (draft.stage == StremioImportStage.Confirm && idle() && !draft.expired && draft.hasScope) mutable.value = draft.copy(confirmed = value)
    }

    /** Not cancellable once started. An uncertain outcome keeps the identical locked selection for retry. */
    fun apply() {
        val draft = current
        val review = draft.review ?: return
        if (draft.stage != StremioImportStage.Confirm || !draft.confirmed || !draft.hasScope || draft.expired || expiredAt(review) || !idle() || !usable()) return
        val request = ++epoch
        val excluded = draft.excluded.sorted()
        mutable.value = draft.copy(busy = StremioImportBusy.Apply, selectionLocked = true, error = null)
        scheduleExpiry()
        start(request) { saveImport(request, review, excluded) }
    }

    private suspend fun saveImport(request: Long, review: StremioReview, excluded: List<String>) {
        try {
            val value = transport.apply(profileId, review.previewId, excluded, review.revision)
            if (!live(request)) return
            mutable.value = current.copy(completion = value, review = null, confirmed = false, stage = StremioImportStage.Complete)
            scheduleExpiry()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!live(request)) return
            if (error is StremioImportFailure && error.code in staleCodes) {
                reset(); mutable.value = current.copy(expired = true, error = stremioStaleCopy)
            } else mutable.value = current.copy(error = stremioImportError(error, true))
        }
    }

    fun back() {
        val draft = current
        if (!idle() || draft.selectionLocked) return
        mutable.value = when (draft.stage) {
            StremioImportStage.Addons -> draft.copy(confirmed = false, stage = StremioImportStage.Connect)
            StremioImportStage.Items -> draft.copy(confirmed = false, stage = if (draft.addons != null) StremioImportStage.Addons else StremioImportStage.Connect)
            StremioImportStage.Confirm -> draft.copy(confirmed = false, stage = StremioImportStage.Items)
            else -> return
        }
    }
    /** "Back to Connect" keeps the draft; Next resumes it without asking for the password again. */
    fun resume() {
        val draft = current
        if (draft.stage == StremioImportStage.Connect && draft.connected && !draft.expired && idle())
            mutable.value = draft.copy(stage = if (draft.addons != null) StremioImportStage.Addons else StremioImportStage.Items)
    }
    /** Cancels a pending preview, or discards an unlocked draft. Never interrupts apply. */
    fun cancel() {
        val draft = current
        if (draft.busy == StremioImportBusy.Preview || (idle() && !draft.selectionLocked)) reset()
    }
    fun changeConnection() { if (idle()) reset() }
    fun startAnother() { if (current.stage == StremioImportStage.Complete) reset() }

    private fun reset() {
        epoch++
        pending?.cancel(); pending = null
        timer?.cancel(); timer = null
        discardPreview()
        val draft = current
        mutable.value = StremioImportState(draft.profileName, draft.restricted, draft.addonsEditable,
            library = draft.library, progress = draft.progress, resets = draft.resets + 1)
    }

    /** Navigation away, profile/account replacement and sign-out end the draft for good. */
    fun dispose() {
        if (disposed) return
        disposed = true
        epoch++
        pending?.cancel(); pending = null
        timer?.cancel(); timer = null
        discardPreview()
        val draft = current
        mutable.value = StremioImportState(draft.profileName, draft.restricted, draft.addonsEditable, resets = draft.resets + 1)
    }

    private fun discardPreview() {
        val old = handle ?: return
        handle = null
        // Start before a retiring connection closes; the outcome is never observed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { transport.discard(profileId, old) } catch (_: Throwable) { }
        }
    }

    private fun scheduleExpiry() {
        timer?.cancel(); timer = null
        val draft = current
        val active: StremioPreview = (if (draft.completion != null) null else draft.review ?: draft.addons) ?: return
        val locked = draft.selectionLocked
        timer = scope.launch {
            delay(maxOf(0L, active.expiresAt * 1000 - now()))
            if (disposed) return@launch
            timer = null
            reset()
            mutable.value = current.copy(expired = true, error = if (locked)
                "This preview expired. Some changes may already have been saved. Start a new preview to review the current account state."
                else stremioStaleCopy)
        }
    }
}
