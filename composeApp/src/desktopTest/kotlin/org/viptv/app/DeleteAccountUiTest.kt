package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.DeleteAccountScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DeleteAccountUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val identity = AccountIdentity("1", "viewer", "Viewer Name", "owner")

    @Test fun theCopyNamesTheAccountAndAsksForAPinOnlyWhereOneMayBeSet() {
        var configured by mutableStateOf<Boolean?>(false)
        val scene = scene { DeleteAccountScreen(identity, configured, busy = false, error = null, load = {}, back = {}, delete = { _, _, _ -> }) }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) ==
                "This deletes Viewer Name’s account, its profiles, their history, My List, add-ons and paired devices. It cannot be undone." })
            assertEquals(2, nodes(scene).count { it.config.contains(SemanticsActions.SetText) },
                "the username and the password, with no PIN field while none is set")
            configured = true; frames(scene)
            assertEquals(3, nodes(scene).count { it.config.contains(SemanticsActions.SetText) })
            configured = null; frames(scene)
            assertEquals(3, nodes(scene).count { it.config.contains(SemanticsActions.SetText) },
                "an account whose PIN state could not be read still offers the field")
        } finally { scene.close() }
    }

    @Test fun thePrimaryActionWaitsForTheExactUsernameAndThePassword() {
        val submissions = mutableListOf<Triple<String, String, String?>>()
        val scene = scene { DeleteAccountScreen(identity, pinConfigured = false, busy = false, error = null, load = {}, back = {}, delete = { user, password, pin -> submissions += Triple(user, password, pin) }) }
        try {
            frames(scene)
            assertTrue(primary(scene).config.contains(SemanticsProperties.Disabled))
            type(scene, 0, "viewe"); frames(scene)
            assertTrue(primary(scene).config.contains(SemanticsProperties.Disabled), "a partial username does not confirm")
            type(scene, 0, "Viewer"); type(scene, 1, "fixture-password"); frames(scene)
            assertTrue(primary(scene).config.contains(SemanticsProperties.Disabled), "the username is typed exactly")
            type(scene, 0, "viewer"); frames(scene)
            click(scene, "Delete account"); frames(scene)
            assertEquals(listOf(Triple<String, String, String?>("viewer", "fixture-password", null)), submissions)
        } finally { scene.close() }
    }

    @Test fun aHouseholdPinIsAskedForAndAWrongOneKeepsBothFieldsInline() {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        val submissions = mutableListOf<Triple<String, String, String?>>()
        val scene = scene { DeleteAccountScreen(identity, pinConfigured = true, busy = busy, error = error, load = {}, back = {}, delete = { user, password, pin ->
            submissions += Triple(user, password, pin); busy = true }) }
        try {
            frames(scene)
            assertEquals(3, nodes(scene).count { it.config.contains(SemanticsActions.SetText) }, "the PIN is asked for too")
            type(scene, 0, "viewer"); type(scene, 1, "fixture-password"); type(scene, 2, "12"); frames(scene)
            assertTrue(primary(scene).config.contains(SemanticsProperties.Disabled), "a short PIN is not sent")
            type(scene, 2, "1234"); frames(scene)
            click(scene, "Delete account"); frames(scene)
            val refused = Triple<String, String, String?>("viewer", "fixture-password", "1234")
            assertEquals(listOf(refused), submissions)
            busy = false; error = "The parent PIN is not correct."; frames(scene)
            assertTrue(nodes(scene).any { text(it) == "The parent PIN is not correct." })
            click(scene, "Delete account"); frames(scene)
            assertEquals(listOf(refused, refused), submissions, "a refused attempt keeps every typed value")
        } finally { scene.close() }
    }

    @Test fun anUnavailableIdentityOffersARetryInsteadOfFields() {
        var loads = 0
        var error by mutableStateOf<String?>(null)
        val scene = scene { DeleteAccountScreen(null, pinConfigured = false, busy = false, error = error, load = { loads++ }, back = {}, delete = { _, _, _ -> }) }
        try {
            frames(scene); assertEquals(1, loads)
            error = "Could not reach your server. Check the connection and try again."; frames(scene)
            assertTrue(nodes(scene).any { text(it) == error })
            assertEquals(0, nodes(scene).count { it.config.contains(SemanticsActions.SetText) })
            click(scene, "Retry the account"); frames(scene)
            assertEquals(2, loads)
        } finally { scene.close() }
    }

    @Test fun theAccountGroupsLastRowOpensThePageAndCancelReturnsToList() {
        val scene = ImageComposeScene(390, 2000, Density(1f)) { ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, { }, {},
                profile = Profile("1", "Viewer", null),
                deleteAccount = { back -> DeleteAccountScreen(identity, false, false, null, {}, back, { _, _, _ -> }) })
        } } }
        try {
            frames(scene)
            click(scene, "Delete account"); frames(scene)
            assertTrue(nodes(scene).any { text(it).startsWith("This deletes Viewer Name’s account") })
            click(scene, "Cancel"); frames(scene)
            assertTrue(nodes(scene).any { text(it).contains("Switch profile") }, "Cancel returns to the settings list")
        } finally { scene.close() }
    }

    private fun scene(content: @Composable () -> Unit) =
        ImageComposeScene(390, 900, Density(1f)) { ViptvTheme { content() } }
    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun primary(scene: ImageComposeScene) = nodes(scene).last { text(it) == "Delete account" && it.config.contains(SemanticsActions.OnClick) }
    private fun clickable(scene: ImageComposeScene, label: String) =
        nodes(scene).lastOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) } ?: fail("no clickable '$label'")
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(clickable(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun type(scene: ImageComposeScene, index: Int, value: String) {
        val target = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.getOrNull(index) ?: fail("no field $index")
        assertTrue(target.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
    }
}