package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.PlatformContext
import org.viptv.app.features.settings.DeviceRecordStore
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.features.settings.WatchOnTvState
import org.viptv.app.features.settings.watchOnTvAvailable
import org.viptv.app.features.settings.watchOnTvSubtitle
import org.viptv.app.features.settings.watchOnTvValue
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WatchOnTvSettingTest {
    @get:Rule val uiThread = UiThreadRule()

    private class MemoryStore(var raw: String? = null) : DeviceRecordStore {
        override fun read() = raw
        override fun write(value: String) { raw = value }
    }

    @Test fun offByDefaultAndOptInPersistsForTheNextReader() {
        val store = MemoryStore()
        val state = WatchOnTvState(store)
        assertFalse(state.value.optedIn, "Watch on TV is off until a TV is paired")
        assertEquals("Off", watchOnTvValue(state.value))

        state.optIn("Living Room", "https://192.0.2.10:7345")
        val next = WatchOnTvState(store)
        assertTrue(next.value.optedIn, "A new reader sees the opt-in")
        assertEquals("Living Room", watchOnTvValue(next.value), "The row reads the persisted TV name")

        state.optOut()
        assertEquals("Off", watchOnTvValue(WatchOnTvState(store).value), "Opting out hides the selection")
        assertEquals("Off", watchOnTvValue(WatchOnTvState(MemoryStore("not json")).value), "An unreadable record falls back to Off")
        assertEquals("Living Room", watchOnTvValue(WatchOnTvState(MemoryStore("""{"optedIn":true,"tvName":"Living Room","tvOrigin":"https://192.0.2.10:7345","later":1}""")).value),
            "A newer build's fields don't throw the record away")
    }

    @Test fun rePairingKeepsTheRemotePreferences() {
        val store = MemoryStore()
        val state = WatchOnTvState(store)
        state.setRemoteSwipe(true)
        state.dismissRemoteTip()
        state.optIn("Living Room", "https://192.0.2.10:7345")
        state.setRemoteSwipe(false)
        state.optIn("Den", "https://192.0.2.11:9000")
        assertEquals("Den", watchOnTvValue(WatchOnTvState(store).value), "Re-pairing keeps the feature on and updates the TV")
        assertTrue(WatchOnTvState(store).value.tipDismissed, "Re-pairing does not reset the dismissed tip")
    }

    @Test fun desktopNeverOffersWatchOnTv() {
        assertFalse(watchOnTvAvailable(PlatformContext.INSTANCE), "Desktop, like iOS and web, has no Watch on TV row")
    }

    @Test fun phoneRowShowsTheCopyAndValueOnlyWhenAvailable() {
        val gated = scene(watchOnTv = false)
        try { frames(gated)
            assertTrue(nodes(gated).none { text(it).contains("Watch on TV") }, "Absent when the platform does not offer it")
        } finally { gated.close() }

        val off = scene(watchOnTv = true, value = "Off")
        try {
            frames(off)
            val row = clickable(off).single { text(it).startsWith("Watch on TV") }
            assertTrue(text(row).contains(watchOnTvSubtitle), "The sub-line carries the remote copy")
            assertTrue(text(row).contains("Off"), "The row reads Off before a TV is paired")
        } finally { off.close() }

        val paired = scene(watchOnTv = true, value = "Living Room")
        try {
            frames(paired)
            val row = clickable(paired).single { text(it).startsWith("Watch on TV") }
            assertTrue(text(row).contains("Living Room"), "The row reads the persisted TV name")
        } finally { paired.close() }
    }

    private fun scene(watchOnTv: Boolean, value: String = "Off"): ImageComposeScene =
        ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneSettings(null, 0, false, null, {}, {}, {}, {}, watchOnTv = watchOnTv, watchOnTvValue = value,
                openWatchOnTv = {})
        } }

    private fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun clickable(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}