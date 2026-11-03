package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.features.entry.TvSignIn
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

/** app#557: a launch that could not reach the saved server offers the retry on the screen it lands on. */
@OptIn(ExperimentalComposeUiApi::class)
class StartupRetryUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val error = "Could not reach your server. Check the connection and try again."

    @Test fun theTvWelcomeStepOffersTheRetryBelowItsReason() {
        var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { TvSignIn("https://fixture.example", false, error, null, { _, _, _ -> }, {}, retry = { retried++ }) }
        }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val reason = nodes.single { text(it) == error }
            val action = nodes.single { text(it) == "Try again" && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(action.boundsInRoot.top >= reason.boundsInRoot.bottom, "The action sits under its reason")
            assertTrue(action.boundsInRoot.bottom <= 1080, "The action is inside the viewport: ${action.boundsInRoot}")
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
        } finally { scene.close() }
    }

    @Test fun theTvWelcomeStepHidesTheRetryWhenThereIsNothingToRetry() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { TvSignIn("https://fixture.example", false, "Session expired. Sign in again.", null, { _, _, _ -> }, {}) }
        }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
            assertTrue(scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.none { text(it) == "Try again" },
                "An expired session offers no retry")
        } finally { scene.close() }
    }

    @Test fun thePhoneSignInChoiceOffersTheRetry() {
        var retried = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            ConnectScreen(false, "https://fixture.example", false, error, null, { _, _, _ -> }, phone = true, retry = { retried++ })
        } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val action = nodes.single { text(it) == "Try again" && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(action.boundsInRoot.top >= 0 && action.boundsInRoot.bottom <= 844, "The action is inside the viewport: ${action.boundsInRoot}")
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
        } finally { scene.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
