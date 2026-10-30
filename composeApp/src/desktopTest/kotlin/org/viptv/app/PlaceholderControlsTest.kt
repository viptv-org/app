package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.entry.TvPairingSession
import org.viptv.app.features.entry.TvPairingSignIn
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlaceholderControlsTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun liveShowsTheFavouriteControlOnlyWhenTheConnectionCanSaveIt() {
        fun live(save: ((LiveChannel) -> Unit)?) = ImageComposeScene(1200, 800, Density(1f)) { ViptvTheme(false) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LiveScreen(listOf(channel()), false, null, null, {}, {}, favoriteChanged = save)
            }
        } }
        val readOnly = live(null)
        try {
            frames(readOnly)
            assertFalse(describes(readOnly, "Favourite channel"), "no favourite star without a save handler")
            assertFalse(readOnlyText(readOnly).contains("Favourite"), "no favourite action in the preview either")
        } finally { readOnly.close() }
        var saves = 0
        val savable = live({ saves++ })
        try {
            frames(savable)
            assertTrue(describes(savable, "Favourite channel"), "the star returns with a save handler")
            assertEquals(true, clickables(savable, "Favourite").first().config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, saves)
        } finally { savable.close() }
    }

    @Test fun tvPreferencesWithoutALoadedSnapshotShowRetryInsteadOfDeadRows() {
        var retries = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                television = true, initialSection = "Playback", tvPreferences = null, changeTvPreferences = {},
                retryPreferences = { retries++ })
        } }
        try {
            frames(scene)
            assertFalse(nodes(scene).any { text(it).startsWith("Default source") || text(it).startsWith("Preferred quality") },
                "no preference row is drawn while its value is unknown")
            assertTrue(nodes(scene).any { text(it) == "These settings are unavailable." })
            assertEquals(true, action(scene, "Retry settings").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retries)
        } finally { scene.close() }
    }

    @Test fun tvPairingOffersNoNewCodeWithoutAChallenge() {
        fun pairing(refresh: (() -> Unit)?, session: TvPairingSession?) = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) { TvPairingSignIn(1f, session, refresh = refresh) }
        } }
        val idle = pairing(null, null)
        try {
            frames(idle)
            assertFalse(nodes(idle).any { text(it) == "New code" }, "no New code action without a refresh handler")
            assertTrue(nodes(idle).any { text(it) == "Sign-in code" })
        } finally { idle.close() }
        var refreshes = 0
        val live = pairing({ refreshes++ }, TvPairingSession("https://example.invalid/device?code=AB12CD34EF", "example.invalid/device", "AB12CD34EF", "14:52"))
        try {
            frames(live)
            assertEquals(true, action(live, "New code").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, refreshes)
        } finally { live.close() }
    }

    private fun channel() = LiveChannel(Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()),
        id = "one", connectionId = "m3u:1")
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun readOnlyText(scene: ImageComposeScene) = nodes(scene).map(::text).filter(String::isNotBlank)
    private fun describes(scene: ImageComposeScene, label: String) =
        nodes(scene).any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description -> description == label } == true }
    private fun clickables(scene: ImageComposeScene, label: String) =
        nodes(scene).filter { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
}
