package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.FakeDownloads.Companion.GIB
import org.viptv.app.FakeDownloads.Companion.NOW
import org.viptv.app.FakeDownloads.Companion.episode
import org.viptv.app.FakeDownloads.Companion.item
import org.viptv.app.downloads.DownloadState
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.downloads.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DownloadsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun tappingAnEpisodeControlShowsItQueuedInTheSameFrame() {
        val fake = FakeDownloads()
        val card = episode(1, 3)
        val scene = scene(fake.actions(clock = System::currentTimeMillis)) { DetailEpisodeCard(card, true, {}, download = true) }
        try {
            frames(scene)
            click(described(scene, "Download S1 E3"))
            scene.render(20 * 16_666_667L).close()
            assertNotNull(nodes(scene).firstOrNull { description(it) == "Downloading 0%" }, "The control shows Queued in the frame of the tap")
            assertEquals(listOf("title:tt-series:3"), fake.calls)
            fake.items.value = listOf(item(card, DownloadState.Downloading, done = GIB))
            scene.renderUntil(what = "the progress ring") { nodes(scene).any { description(it) == "Downloading 50%" } }
            fake.items.value = listOf(item(card, DownloadState.Completed, done = 2 * GIB))
            scene.renderUntil(what = "the downloaded check") { nodes(scene).any { description(it) == "Downloaded" } }
        } finally { scene.close() }
    }

    @Test fun aPlatformWithoutDownloadsDrawsNoControl() {
        val scene = ImageComposeScene(400, 300, Density(1f)) { ViptvTheme { DetailEpisodeCard(episode(1, 3), true, {}, download = true) } }
        try {
            frames(scene)
            assertTrue(nodes(scene).none { description(it).startsWith("Download") })
        } finally { scene.close() }
    }

    @Test fun downloadsPageShowsEvictionStorageLimitAndKeepsKeptItemsOnDeleteWatched() {
        val expiring = item(episode(1, 1), DownloadState.Completed, done = 2 * GIB, watchedAt = NOW - 3_600_000L, expiresAt = NOW + 23 * 3_600_000L)
        val kept = item(episode(1, 2), DownloadState.Completed, done = 2 * GIB, watchedAt = NOW - 3_600_000L, keep = true)
        val waiting = item(episode(1, 3), DownloadState.WaitingForSpace, done = GIB / 2)
        val fake = FakeDownloads(listOf(expiring, kept, waiting), evicted = 2)
        val actions = fake.actions()
        val scene = scene(actions, height = 1200) { DownloadsScreen(actions, offline = false, browse = {}, showTitle = false) }
        try {
            frames(scene)
            val shown = texts(scene)
            assertTrue("Removed 2 old downloads to make space" in shown, "$shown")
            assertTrue(STORAGE_LIMIT_REACHED in shown && "Raise limit" in shown && "Manage" in shown)
            assertTrue(shown.any { it.contains("Watched · deletes in 23 h") } && shown.any { it.contains("Watched · kept") }, "$shown")
            assertEquals(1, shown.count { it == "Keep" }, "Only the expiring watched row offers Keep")
            click(clickable(scene, "Delete watched"))
            frames(scene)
            assertEquals(listOf("delete:${expiring.id}", "notice:Deleted 1 watched download"), fake.calls)
            click(described(scene, "Dismiss"))
            assertEquals(0, fake.evictionNotice.value)
            click(clickable(scene, "Raise limit"))
            frames(scene)
            click(clickable(scene, "25 GB"))
            assertEquals(25L * GIB, fake.settings.value.storageLimitBytes)
        } finally { scene.close() }
    }

    @Test fun emptyPageOffersBrowseOnlyWhileOnline() {
        val actions = FakeDownloads().actions()
        val online = scene(actions) { DownloadsScreen(actions, offline = false, browse = {}, showTitle = true) }
        try { frames(online); assertTrue(DOWNLOADS_EMPTY_TITLE in texts(online) && "Browse" in texts(online)) } finally { online.close() }
        val away = scene(actions) { DownloadsScreen(actions, offline = true, browse = {}, showTitle = true) }
        try { frames(away); assertFalse("Browse" in texts(away)) } finally { away.close() }
    }

    @Test fun settingsChangeTheDeviceDownloadSettings() {
        val fake = FakeDownloads()
        val scene = scene(fake.actions(), height = 800) { DownloadSettingsPage(fake, compact = true) }
        try {
            frames(scene)
            assertTrue(texts(scene).any { it.contains("Storage limit") && it.contains("50 GB") })
            assertTrue(texts(scene).any { it.contains("Delete watched downloads") && it.contains("After 1 day") })
            click(clickable(scene) { it.contains("Delete watched downloads") })
            frames(scene)
            click(clickable(scene, "After 1 week"))
            assertEquals(7 * 86_400_000L, fake.settings.value.deleteWatchedAfterMillis)
            click(clickable(scene) { it.contains("Download on Wi‑Fi only") })
            assertFalse(fake.settings.value.wifiOnly)
        } finally { scene.close() }
    }

    private fun scene(actions: DownloadActions, width: Int = 400, height: Int = 600, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            val index = rememberDownloadIndex(actions)
            CompositionLocalProvider(LocalDownloadActions provides actions, LocalDownloadIndex provides index) {
                Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
            }
        } }

    private fun frames(scene: ImageComposeScene) = repeat(12) { scene.render(it * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text)
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()
    private fun described(scene: ImageComposeScene, label: String) =
        nodes(scene).lastOrNull { description(it) == label && it.config.contains(SemanticsActions.OnClick) } ?: fail("no control '$label'")
    private fun clickable(scene: ImageComposeScene, label: String) = clickable(scene) { it == label }
    private fun clickable(scene: ImageComposeScene, match: (String) -> Boolean) =
        nodes(scene).lastOrNull { match(text(it)) && it.config.contains(SemanticsActions.OnClick) } ?: fail("no clickable match in ${texts(scene)}")
    private fun click(node: SemanticsNode) { assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()) }
}
