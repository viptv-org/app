package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.entry.BrowserAccountForm
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SignUpRulesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text).filter { it.isNotBlank() }
    private fun shows(scene: ImageComposeScene, value: String) = texts(scene).any { it.contains(value) }
    private fun fields(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }
    private fun type(node: SemanticsNode, value: String) =
        assertTrue(node.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)), "cannot type into the field")

    private fun capture(scene: ImageComposeScene, name: String) {
        val dir = System.getenv("VIPTV_SIGNUP_CAPTURE_DIR") ?: return
        java.io.File(dir).mkdirs()
        repeat(10) { scene.render(it * 100_000_000L).close() }
        scene.render(1_100_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) } }
    }

    private fun form(recovering: Boolean, width: Int = 1280, height: Int = 800): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            BrowserAccountForm(recovering, false, null, {}, { _, _, _ -> }, { _, _, _ -> })
        } }
        repeat(20) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    @Test fun theSignUpFormStatesEveryRuleBeforeItBreaks() {
        val scene = form(recovering = false)
        try {
            assertTrue(shows(scene, "Letters, numbers, or . _ - @. Up to 80 characters."), "the username rule")
            assertTrue(texts(scene).any { it == "Up to 80 characters." }, "the display-name rule")
            assertTrue(shows(scene, "At least 12 characters."), "the password rule, as change-password states it")
            val (_, _, password, _) = fields(scene)
            type(password, "short")
            repeat(5) { scene.render(it * 16_666_667L).close() }
            assertTrue(shows(scene, "Use at least 12 characters."), "a short password says what it breaks")
            assertFalse(shows(scene, "At least 12 characters."), "the error replaces the hint")
            val field = fields(scene)[2]
            val error = nodes(scene).single { text(it) == "Use at least 12 characters." }
            assertTrue(error.boundsInRoot.top >= field.boundsInRoot.bottom, "the reason sits under its field")
            assertTrue(nodes(scene).single { text(it) == "Create account" && it.config.contains(SemanticsActions.OnClick) }
                .config.contains(SemanticsProperties.Disabled), "submit stays disabled while a rule is broken")
            capture(scene, "signup-desktop")
        } finally { scene.close() }
    }

    @Test fun theSignUpFormTurnsTheFieldsGreenAgainWhenTheyPass() {
        val scene = form(recovering = false)
        try {
            val (username, name, password, confirmation) = fields(scene)
            type(username, "Bob Smith"); type(name, "Bob"); type(password, "short"); type(confirmation, "nope")
            repeat(5) { scene.render(it * 16_666_667L).close() }
            assertTrue(shows(scene, "Use letters, numbers, or . _ - @ only."), "a space breaks the username")
            assertTrue(shows(scene, "Passwords do not match."), "the confirmation keeps its line")
            type(fields(scene)[0], "bob"); type(fields(scene)[2], "twelve-chars"); type(fields(scene)[3], "twelve-chars")
            repeat(5) { scene.render(it * 16_666_667L).close() }
            assertFalse(shows(scene, "Use letters, numbers, or . _ - @ only."), "the username error clears")
            assertFalse(shows(scene, "Use at least 12 characters."), "the password error clears")
            assertTrue(shows(scene, "At least 12 characters."), "and its hint returns")
            assertFalse(nodes(scene).single { text(it) == "Create account" && it.config.contains(SemanticsActions.OnClick) }
                .config.contains(SemanticsProperties.Disabled), "submit unlocks when every rule passes")
        } finally { scene.close() }
    }

    @Test fun theRecoveryFormStatesTheCodeAndPasswordRules() {
        val scene = form(recovering = true, width = 390, height = 844)
        try {
            assertTrue(shows(scene, "The code you saved when creating your account."), "where the recovery code came from")
            assertTrue(shows(scene, "At least 12 characters."), "the new-password rule")
            val code = fields(scene)[1]
            type(code, "a".repeat(257))
            repeat(5) { scene.render(it * 16_666_667L).close() }
            assertTrue(shows(scene, "Use at most 256 characters."), "an over-long code says so")
            capture(scene, "recovery-phone")
            assertTrue(nodes(scene).single { text(it) == "Use at most 256 characters." }.boundsInRoot.top >=
                fields(scene)[1].boundsInRoot.bottom, "the reason sits under the code field")
        } finally { scene.close() }
    }
}