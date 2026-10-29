package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*

enum class StremioImportStage(val step: Int, val title: String) {
    Connect(1, "Connect Stremio"), Addons(2, "Choose add-ons"), Items(3, "Review items"), Confirm(4, "Confirm import"), Complete(0, "Import complete")
}
enum class StremioImportBusy(val label: String, val expectation: String) {
    Preview("Reading Stremio for a read-only preview…", "Large Stremio libraries can take up to a minute. Nothing is saved."),
    Review("Verifying metadata and preparing named items…", "This can take up to a minute for large libraries or many add-ons. Nothing is saved yet."),
    Apply("Saving the confirmed import…", "This can take up to a minute. Stay on this screen to see the result.")
}

enum class StremioDetailsPhase { Running, Ready, Background }
data class StremioTitleDetails(val done: Int, val total: Int, val phase: StremioDetailsPhase, val filled: Int? = null)

data class StremioImportState(
    val profileName: String,
    val restricted: Boolean,
    val stage: StremioImportStage = StremioImportStage.Connect,
    val email: String = "",
    val library: Boolean = true,
    val progress: Boolean = true,
    val addons: StremioAddonPreview? = null,
    val selectedAddons: Set<String> = emptySet(),
    val failedAddons: Set<String> = emptySet(),
    val failedReasons: Map<String, String> = emptyMap(),
    val failedReports: Int = 0,
    val review: StremioReview? = null,
    val excluded: Set<String> = emptySet(),
    val reviewedAddons: String? = null,
    val completion: StremioCompletion? = null,
    val confirmed: Boolean = false,
    val selectionLocked: Boolean = false,
    val expired: Boolean = false,
    val busy: StremioImportBusy? = null,
    val error: String? = null,
    val notice: String? = null,
    val resets: Int = 0,
    val details: StremioTitleDetails? = null,
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
internal const val stremioTimeoutCopy = "Stremio or the server took too long to respond. Try again."
internal const val stremioReviewTimeoutCopy = "Stremio or the server took too long to respond. Try again, or choose fewer add-ons."
internal const val stremioUnreachableCopy = "Could not reach the VIPTV server. Check your connection and try again."
internal const val stremioReviewStoppedCopy = "Verification stopped. Your add-on choices are kept; change them or try again."

internal fun stremioImportError(failure: Throwable, applying: Boolean, reviewing: Boolean = false): String {
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
        "account_session_required" -> return "This session can't add add-ons to your account. Continue without add-ons, or try again from VIPTV on the web."
        null -> when (failure.status) {
            401 -> return "Sign in again to continue."
            429 -> return "Too many requests. Wait a moment and try again."
        }
    }
    if (failure is StremioInvalidResponse && failure.message in setOf("Invalid review", "Invalid add-ons", "Replaced review"))
        return "The server returned an unexpected import review. Start a new preview or update VIPTV."
    if (applying) return stremioUncertainApplyCopy
    if (failure is StremioTransportFailure) return when {
        !failure.timedOut -> stremioUnreachableCopy
        reviewing -> stremioReviewTimeoutCopy
        else -> stremioTimeoutCopy
    }
    return "Could not prepare the import. Check your details or add-on choices and try again."
}

class StremioImportSession internal constructor(
    private val scope: CoroutineScope,
    private val transport: StremioImportTransport,
    val profileId: String,
    profileName: String,
    restricted: Boolean,
    private val now: () -> Long = ::epochMillis,
    internal val owned: () -> Boolean = { true },
    internal val changed: () -> Unit = {},
    private val detailsPoll: Long = DETAILS_POLL_MILLIS,
    private val detailsLimit: Long = DETAILS_LIMIT_MILLIS,
) {
    private val mutable = MutableStateFlow(StremioImportState(profileName, restricted))
    val state = mutable.asStateFlow()
    private val current get() = mutable.value
    private var epoch = 0L
    private var pending: Job? = null
    private var timer: Job? = null
    private var details: Job? = null
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

    fun connect(email: String, password: String) {
        val draft = current
        if (!idle() || !usable() || draft.connected || draft.completion != null || email.isBlank() || password.isEmpty()) return
        val request = ++epoch
        val address = email.trim()
        mutable.value = draft.copy(error = null, notice = null, busy = StremioImportBusy.Preview)
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
                        selectedAddons = value.addons.filter { it.status != "unavailable" }.map { it.id }.toSet())
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

    private fun canChooseAddons() = idle() && !current.expired && current.stage == StremioImportStage.Addons
    fun toggleAddon(id: String, selected: Boolean) {
        val addon = current.addons?.addons?.find { it.id == id } ?: return
        if (!canChooseAddons() || (selected && addon.status == "unavailable")) return
        mutable.value = current.copy(confirmed = false, notice = null,
            selectedAddons = if (selected) current.selectedAddons + id else current.selectedAddons - id,
            failedAddons = if (selected) current.failedAddons else current.failedAddons - id,
            failedReasons = if (selected) current.failedReasons else current.failedReasons - id)
    }
    fun selectAllAddons() {
        val addons = current.addons ?: return
        if (canChooseAddons()) mutable.value = current.copy(confirmed = false, notice = null,
            selectedAddons = addons.addons.filter { it.status != "unavailable" }.map { it.id }.toSet())
    }
    fun clearAddons() { if (canChooseAddons()) mutable.value = current.copy(confirmed = false, notice = null, selectedAddons = emptySet(),
        failedAddons = emptySet(), failedReasons = emptyMap()) }
    fun uncheckFailedAddons() {
        if (canChooseAddons() && current.failedAddons.isNotEmpty()) mutable.value = current.copy(confirmed = false, notice = null,
            selectedAddons = current.selectedAddons - current.failedAddons, failedAddons = emptySet(), failedReasons = emptyMap())
    }
    fun continueWithoutAddons() {
        if (!canChooseAddons()) return
        mutable.value = current.copy(selectedAddons = emptySet(), failedAddons = emptySet(), failedReasons = emptyMap(), confirmed = false)
        verify(emptySet())
    }
    fun reviewItems() = verify(current.selectedAddons)

    private fun verify(chosen: Set<String>) {
        val draft = current
        val addons = draft.addons ?: return
        if (draft.expired || !idle() || !usable() || expiredAt(addons)) return
        val key = chosen.sorted().joinToString(",")
        if (draft.review != null && draft.reviewedAddons == key) {
            mutable.value = draft.copy(error = null, notice = null, stage = StremioImportStage.Items); return
        }
        val request = ++epoch
        mutable.value = draft.copy(reviewedAddons = null, error = null, notice = null, failedAddons = emptySet(), failedReasons = emptyMap(),
            busy = StremioImportBusy.Review)
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
                mutable.value = addons.addons.map { it.id }.filter { it in error.failedAddons }.toSet().let { failed ->
                    current.copy(failedAddons = failed, failedReasons = error.failedReasons.filterKeys { it in failed },
                        failedReports = current.failedReports + 1) }
            if (error is StremioImportFailure && error.code in staleCodes) { reset(); mutable.value = current.copy(expired = true) }
            mutable.value = current.copy(error = stremioImportError(error, false, reviewing = true))
        }
    }

    private fun canChooseItems() = idle() && !current.expired && !current.selectionLocked && current.review != null
    fun setItemsIncluded(ids: Collection<String>, included: Boolean) {
        val review = current.review ?: return
        if (!canChooseItems()) return
        val wanted = ids as? Set<String> ?: ids.toHashSet()
        val selectable = review.items.filter { it.selectable && it.id in wanted }.map { it.id }
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
            changed()
            followDetails(request)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!live(request)) return
            if (error is StremioImportFailure && error.code in staleCodes) {
                reset(); mutable.value = current.copy(expired = true, error = stremioStaleCopy)
            } else mutable.value = current.copy(error = stremioImportError(error, true))
        }
    }

    private fun followDetails(request: Long) {
        details?.cancel()
        mutable.value = current.copy(details = StremioTitleDetails(0, 0, StremioDetailsPhase.Running))
        val until = now() + detailsLimit
        details = scope.launch {
            var failures = 0
            fun show(done: Int, total: Int, phase: StremioDetailsPhase, filled: Int? = current.details?.filled) {
                mutable.value = current.copy(details = StremioTitleDetails(done, total, phase, filled))
            }
            while (true) {
                val answer = try { Result.success(transport.details(profileId)) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Result.failure(error) }
                if (!live(request)) return@launch
                val status = answer.getOrNull()
                val shown = current.details ?: return@launch
                when {
                    answer.isFailure -> if (++failures >= DETAILS_FAILURES) return@launch show(shown.done, shown.total, StremioDetailsPhase.Background)
                    status == null || (!status.running && status.total == 0) -> { mutable.value = current.copy(details = null); return@launch }
                    !status.running -> { show(status.done, status.total, StremioDetailsPhase.Ready, status.filled); changed(); return@launch }
                    else -> { failures = 0; show(status.done, status.total, StremioDetailsPhase.Running, status.filled) }
                }
                if (now() >= until) return@launch show(current.details?.done ?: 0, current.details?.total ?: 0, StremioDetailsPhase.Background)
                delay(detailsPoll)
            }
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
    fun resume() {
        val draft = current
        if (draft.stage == StremioImportStage.Connect && draft.connected && !draft.expired && idle())
            mutable.value = draft.copy(stage = if (draft.addons != null) StremioImportStage.Addons else StremioImportStage.Items)
    }
    fun cancel() {
        val draft = current
        when {
            draft.busy == StremioImportBusy.Review -> {
                epoch++
                pending?.cancel(); pending = null
                mutable.value = draft.copy(busy = null, reviewedAddons = null, confirmed = false, error = null,
                    notice = stremioReviewStoppedCopy, stage = StremioImportStage.Addons)
            }
            draft.busy == StremioImportBusy.Preview || (idle() && !draft.selectionLocked) -> reset()
        }
    }
    fun changeConnection() { if (idle()) reset() }
    fun startAnother() { if (current.stage == StremioImportStage.Complete) reset() }

    private fun reset() {
        epoch++
        pending?.cancel(); pending = null
        timer?.cancel(); timer = null
        details?.cancel(); details = null
        discardPreview()
        val draft = current
        mutable.value = StremioImportState(draft.profileName, draft.restricted,
            library = draft.library, progress = draft.progress, resets = draft.resets + 1)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        epoch++
        pending?.cancel(); pending = null
        timer?.cancel(); timer = null
        details?.cancel(); details = null
        discardPreview()
        val draft = current
        mutable.value = StremioImportState(draft.profileName, draft.restricted, resets = draft.resets + 1)
    }

    private fun discardPreview() {
        val old = handle ?: return
        handle = null
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

internal const val DETAILS_POLL_MILLIS = 2_000L
internal const val DETAILS_LIMIT_MILLIS = 5 * 60_000L
internal const val DETAILS_FAILURES = 3
