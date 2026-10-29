package org.viptv.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.features.settings.StremioImportScreen
import org.viptv.app.state.*
import kotlin.time.TimeSource

@Composable
internal fun PreviewImportDetails(compact: Boolean) {
    val scope = rememberCoroutineScope()
    val session = remember { StremioImportSession(scope, PreviewImportTransport(), PreviewImportTransport.PROFILE, "Preview profile", false) }
    DisposableEffect(session) { onDispose { session.dispose() } }
    LaunchedEffect(session) {
        session.connect("preview@example.invalid", "preview")
        session.state.first { it.stage == StremioImportStage.Addons && it.busy == null }
        session.continueWithoutAddons()
        session.state.first { it.stage == StremioImportStage.Items && it.busy == null }
        session.toConfirm(); session.setConfirmed(true); session.apply()
    }
    Box(Modifier.fillMaxSize().padding(if (compact) 0.dp else 32.dp)) {
        StremioImportScreen(session, "Preview profile", compact)
    }
}

private class PreviewImportTransport : StremioImportTransport {
    private var applied: TimeSource.Monotonic.ValueTimeMark? = null
    private val expires get() = epochMillis() / 1000 + 600
    private val none = mapOf("favorites_to_add" to 0, "progress_to_add" to 0, "progress_to_update" to 0, "existing_preserved" to 0, "already_imported" to 0)

    override suspend fun preview(profile: String, email: String, password: String, library: Boolean, progress: Boolean): StremioPreview =
        StremioAddonPreview(HANDLE, profile, expires, emptyList())
    override suspend fun review(profile: String, preview: String, addons: List<String>, expectedRevision: Long): StremioReview {
        val items = List(TITLES) { StremioReviewItem("preview_title_${it.toString().padStart(8, '0')}", "Title ${it + 1}", "movie", "add", "none",
            "ready", none + ("favorites_to_add" to 1), true) }
        return StremioReview(HANDLE, profile, expires, mapOf("source_items" to TITLES, "favorites_to_add" to TITLES, "progress_to_add" to 0,
            "progress_to_update" to 0, "existing_preserved" to 0, "already_imported" to 0, "needs_review" to 0, "skipped_items" to 0),
            items, 0, expectedRevision + 1)
    }
    override suspend fun apply(profile: String, preview: String, excluded: List<String>, revision: Long): StremioCompletion {
        applied = TimeSource.Monotonic.markNow()
        return StremioCompletion(profile, mapOf("favorites_added" to TITLES - excluded.size, "progress_added" to 0, "progress_updated" to 0,
            "existing_preserved" to 0, "already_imported" to 0, "needs_review" to 0, "skipped_items" to 0), excludedItems = excluded.size)
    }
    override suspend fun discard(profile: String, preview: String) = Unit
    override suspend fun details(profile: String): StremioDetailsStatus {
        val seconds = (applied ?: return StremioDetailsStatus(false, 0, 0)).elapsedNow().inWholeMilliseconds / 1000.0
        if (seconds < 3) return StremioDetailsStatus(true, 0, 0)
        val done = minOf(TITLES, ((seconds - 3) * TITLES / 45).toInt())
        return StremioDetailsStatus(done < TITLES, done, TITLES, done)
    }

    companion object {
        const val PROFILE = "preview"
        const val HANDLE = "preview_import_handle_0001"
        const val TITLES = 162
    }
}
