package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.entry.TvSignIn
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class StartupFailureUiTest {
    @Test fun expiredRestoreReasonIsVisibleOnTheActualWelcomeStepAndSignInRemainsEnabled() {
        val reason = "Session expired. Sign in again."
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { TvSignIn("", false, reason, null, { _, _, _ -> }, {}) }
        }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val message = nodes.single { text(it) == reason }
            assertTrue(message.boundsInRoot.top >= 656 && message.boundsInRoot.bottom < 960)
            val signIn = nodes.single { text(it) == "Sign in" && it.config.contains(SemanticsActions.OnClick) }
            assertFalse(signIn.config.contains(SemanticsProperties.Disabled))
        } finally { scene.close() }
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
