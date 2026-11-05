package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.annotation.DelicateCoilApi
import org.junit.Rule
import org.viptv.app.features.details.BrowserDetailsDialog
import org.viptv.app.features.entry.MobilePinScreen
import org.viptv.app.features.entry.TvPinScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class TapTargetTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun everyClickableTargetOnEveryPageIsAtLeast44dp() {
        val violations = buildList {
            for ((width, height) in listOf(390 to 844, 1280 to 900)) {
                AppShell(width, height).use { shell ->
                    for ((name, state) in a11yPages) {
                        shell.show(state)
                        addAll(shell.violations().map { "$name at ${width}px: $it" })
                    }
                }
            }
            addAll(surfaceViolations())
        }
        assertTrue(violations.isEmpty(), "clickable targets under ${TapTargetPixels.toInt()}dp:\n${violations.joinToString("\n")}")
    }

    private fun surfaceViolations(): List<String> = buildList {
        addAll(render("Player, phone", 390, 844) {
            PlayerScreen(null, true, null, {}, {}, {}, title = PreviewData.playerTitle, phone = true,
                preview = PlayerFact.Progress(842_000, 3_132_000))
        })
        addAll(render("Player, desktop", 1280, 720) {
            PlayerScreen(null, false, null, {}, {}, {}, title = PreviewData.playerTitle, phone = false,
                preview = PlayerFact.Progress(842_000, 3_132_000))
        })
        addAll(render("Profile PIN, phone", 390, 844) {
            MobilePinScreen(a11yLockedProfile, "12", false, null, {}, {}, {}, {})
        })
        addAll(render("Profile PIN, TV", 1920, 1080, television = true) {
            TvPinScreen(a11yLockedProfile, "12", false, null, {}, {}, {}, {})
        })
        addAll(render("Details dialog, desktop", 1280, 900) {
            BrowserDetailsDialog(PreviewData.detail, false, null, {}, {})
        })
        addAll(render("Source panel, tablet", 1024, 768) {
            SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, {}, PreviewData.detail, tablet = true)
        })
        addAll(render("Source panel, TV", 1920, 1080, television = true) {
            SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, {}, PreviewData.detail, television = true)
        })
    }

    private fun render(name: String, width: Int, height: Int, television: Boolean = false,
        content: @Composable () -> Unit): List<String> {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) {
                Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
            }
        }
        try {
            scene.renderFrames(count = 80)
            return scene.semanticsNodes().tapTargetViolations(width, height).map { "$name: $it" }
        } finally { scene.close() }
    }
}