package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.InputSurface
import org.viptv.app.components.LocalActivationKeys
import org.viptv.app.components.bindsActivationKeys
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopActivationKeysTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun onlyThePointerOnlyDesktopClientOmitsActivationKeys() {
        assertEquals(
            mapOf(InputSurface.Television to true, InputSurface.Touch to true,
                InputSurface.Browser to true, InputSurface.Desktop to false),
            InputSurface.entries.associateWith { it.bindsActivationKeys })
    }

    @Test fun theThemeCarriesTheSurfaceDecisionToTheControlsThatBindKeys() {
        assertEquals(true, provided(television = true), "a television keeps its transport keys")
        assertEquals(true, provided(television = false), "phone, tablet and browser keep them")
        assertEquals(false, provided(television = false, activationKeys = false), "the pointer-only desktop client binds none")
    }

    private fun provided(television: Boolean, activationKeys: Boolean = true): Boolean {
        var seen = false
        ImageComposeScene(320, 200, Density(1f)) {
            ViptvTheme(television = television, activationKeys = activationKeys) {
                seen = LocalActivationKeys.current
                Box(Modifier)
            }
        }.use { it.render(0).close() }
        return seen
    }
}
