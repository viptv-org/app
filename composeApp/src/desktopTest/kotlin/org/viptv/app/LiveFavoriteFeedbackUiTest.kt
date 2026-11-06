package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.PendingMutationUi
import org.viptv.app.features.live.*
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LiveFavoriteFeedbackUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun channel(favorite: Boolean = false) = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Channel 1", badges = emptyList()),
        number = "1", id = "one", connectionId = "m3u:1", favorite = favorite,
        programs = listOf(LiveProgram("p1", "Evening News", 0, 30)))

    private fun target() = liveFavoriteTarget("one")

    private fun feedback(pending: Boolean = false, failure: String? = null) = PendingMutationUi(
        when {
            failure != null -> PendingMutations().failed(target(), failure)
            pending -> PendingMutations().starting(target())
            else -> PendingMutations()
        }, retry = { retried++ })

    private var retried = 0

    private fun scene(width: Int, height: Int, ui: PendingMutationUi, television: Boolean = false, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                CompositionLocalProvider(LocalPendingMutation provides ui) { content() }
            } }
        }

    @Test fun theStarFlipsAndSpinsInTheFrameOfThePressAtPhoneWidth() {
        val row = @Composable { favorite: Boolean -> LiveChannelRow(channel(favorite), 15, {}, {}) }
        val idle = pixels(390, 200, feedback()) { row(false) }
        val flipped = pixels(390, 200, feedback()) { row(true) }
        val pending = pixels(390, 200, feedback(pending = true)) { row(true) }
        assertFalse(idle.contentEquals(flipped), "a favourite draw differs from an unfavourited one")
        assertFalse(flipped.contentEquals(pending), "a write in flight draws a pending mark on the star itself")

        val scene = scene(390, 200, feedback(pending = true)) { row(true) }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }, "the star carries a spinner while the write runs")
            capture(scene, "phone-390-favourite-pending")
        } finally { scene.close() }
    }

    @Test fun aFailedFavoritePutsTheStarBackAndOffersRetryOnTheRow() {
        val scene = scene(390, 200, feedback(failure = "Could not save this change. Try again.")) { LiveChannelRow(channel(false), 15, {}, {}) }
        try {
            frames(scene)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains("Could not save this change. Try again."), "the row carries the failure beside the star")
            assertTrue(texts.contains("Retry"))
            assertTrue(nodes(scene).first { text(it) == "Retry" }.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
            capture(scene, "phone-390-favourite-failed")
        } finally { scene.close() }
    }

    @Test fun thePreviewButtonSaysSavingThenCarriesTheFailureBesideIt() {
        for (width in listOf(1280, 1440)) {
            val preview = @Composable { favorite: Boolean -> LivePreview(channel(favorite), LiveProgram("p1", "Evening News", 0, 30), 15, phone = false, television = false,
                scale = 1f, watch = {}, favourite = {}, guide = {}) }
            val idle = pixels(width, 420, feedback()) { preview(false) }
            val pending = pixels(width, 420, feedback(pending = true)) { preview(true) }
            assertFalse(idle.contentEquals(pending), "${width}px: the preview button answers the press")

            val busy = scene(width, 420, feedback(pending = true)) { preview(true) }
            try {
                frames(busy)
                assertTrue(nodes(busy).map(::text).contains("Saving…"))
                capture(busy, "desktop-$width-favourite-pending")
            } finally { busy.close() }

            val failed = scene(width, 420, feedback(failure = "Could not save this change. Try again.")) { preview(false) }
            try {
                frames(failed)
                assertTrue(nodes(failed).map(::text).contains("Could not save this change. Try again."))
                capture(failed, "desktop-$width-favourite-failed")
            } finally { failed.close() }
        }
    }

    @Test fun theTvActionSpinsWhileSavingAndRetriesOnTheSameButtonOnceItFails() {
        val pending = scene(1920, 1080, feedback(pending = true), television = true) { TvLivePreview(channel(true), LiveProgram("p1", "Evening News", 0, 30), 15, 1f, favorite = {}, watch = {}) }
        try {
            frames(pending)
            assertTrue(nodes(pending).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }, "the TV action spins while the write runs")
            capture(pending, "tv-1920-favourite-pending")
        } finally { pending.close() }

        val failed = scene(1920, 1080, feedback(failure = "Could not save this change. Try again."), television = true) { TvLivePreview(channel(false), LiveProgram("p1", "Evening News", 0, 30), 15, 1f, favorite = {}, watch = {}) }
        try {
            frames(failed)
            assertTrue(nodes(failed).map(::text).contains("Could not save this change. Try again."))
            val action = nodes(failed).first { text(it) == "Retry" }
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke(), "the failed action retries the same write")
            assertEquals(1, retried)
            capture(failed, "tv-1920-favourite-failed")
        } finally { failed.close() }
    }

    private fun pixels(width: Int, height: Int, ui: PendingMutationUi, content: @Composable () -> Unit): ByteArray {
        val scene = scene(width, height, ui, content = content)
        return try { frames(scene); scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { it.bytes } } }
        finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun capture(scene: ImageComposeScene, name: String) {
        val directory = System.getenv("VIPTV_LIVE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
}