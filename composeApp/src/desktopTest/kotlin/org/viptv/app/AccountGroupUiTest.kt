package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.AccountIdentityScreen
import org.viptv.app.features.settings.HouseholdPinSettings
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountGroupUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val management = ProfileManagement(
        listOf(ServerProfile("1", "Viewer", kids = true, max_age = 10)),
        ProfileCapabilities(FeatureAvailability("Available"), FeatureAvailability("Available"), FeatureAvailability("Available"),
            FeatureAvailability("Available"), FeatureAvailability("Unsupported"), FeatureAvailability("Available")),
        ParentAccess(pin_configured = true, unlocked = true, restricted = false))

    @Test fun accountRowOpensTheIdentityTheHostReported() {
        val scene = scene(AccountIdentity("1", "viewer", "Viewer Name", "member"))
        try {
            frames(scene)
            click(scene, "Account"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Name Viewer Name" })
            assertTrue(nodes(scene).any { text(it) == "Username @viewer" })
            assertTrue(nodes(scene).any { text(it) == "Role Member" })
            assertFalse(nodes(scene).any { text(it) == "Email" || text(it).startsWith("Email ") }, "accounts have no email row")
            click(scene, "Back"); frames(scene)
            assertTrue(nodes(scene).any { text(it).contains("Switch profile") }, "Back returns to the settings list")
        } finally { scene.close() }
    }

    @Test fun householdPinRowOpensItsPageAndRemovalStaysBlockedWhileKidsModeIsOn() {
        val scene = scene(AccountIdentity("1", "viewer", "Viewer Name", "owner"))
        try {
            frames(scene)
            click(scene, "Household PIN"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Use a 4–8 digit PIN to protect leaving kids profiles. Turn off kids mode on all profiles before removing the PIN." })
            assertTrue(nodes(scene).any { text(it) == "Turn off kids restrictions on all profiles before removing the PIN." })
            assertTrue(nodes(scene).first { text(it) == "Remove PIN" }.config.contains(SemanticsProperties.Disabled))
            click(scene, "Back"); frames(scene)
            assertTrue(nodes(scene).any { text(it).contains("Switch profile") })
        } finally { scene.close() }
    }

    @Test fun sourceIntegrationsRowOpensItsOwnPageInsteadOfTheAddonList() {
        val scene = scene(AccountIdentity("1", "viewer", "Viewer Name", "member"))
        try {
            frames(scene)
            click(scene, "Source integrations"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Metadata provider" })
            click(scene, "Back"); frames(scene)
            click(scene, "Add-ons"); frames(scene)
            assertFalse(nodes(scene).any { text(it) == "Metadata provider" }, "Add-ons no longer carries the source configuration")
        } finally { scene.close() }
    }

    private fun scene(identity: AccountIdentity?) = ImageComposeScene(390, 2600, Density(1f)) {
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, { }, {},
                profile = Profile("1", "Viewer", null),
                account = { AccountIdentityScreen(identity, false, null, {}) },
                householdPin = { HouseholdPinSettings(management, false, null, null, 0, {}, {}, { _, _ -> }) },
                sourceIntegrations = { _, _, _ -> Text("Metadata provider") },
                addDevice = { Text("Add device page") })
        } }
    }

    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun clickable(scene: ImageComposeScene, label: String) =
        assertNotNull(nodes(scene).firstOrNull { labelled(it, label) && it.config.contains(SemanticsActions.OnClick) }, "no clickable '$label'")
    private fun labelled(node: SemanticsNode, label: String): Boolean {
        val held = text(node)
        return held == label || held.startsWith("$label ")
    }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(clickable(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
}
