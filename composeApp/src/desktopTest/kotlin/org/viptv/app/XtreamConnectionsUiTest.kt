package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.XtreamConnectionsSettings
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class XtreamConnectionsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private var frameTime = 0L

    private val connections = listOf(
        XtreamConnection(1, "Living room", XtreamScopes(true, true, true, true), true, XtreamRefresh("succeeded", null)),
        XtreamConnection(2, "Bedroom", XtreamScopes(true, true, false, false), true,
            XtreamRefresh("failed", "The provider catalog could not be refreshed.")))

    @Test fun thePhonePageShowsEveryConnectionAndNeverRendersStoredSecrets() {
        val scene = scene(390, 1400) { page(compact = true) }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Xtream connections" })
            assertTrue(nodes(scene).any { text(it) == "Living room" })
            assertTrue(nodes(scene).any { text(it) == "Bedroom" })
            assertTrue(nodes(scene).any { text(it) == "Enabled · Up to date" })
            assertTrue(nodes(scene).any { text(it) == "Enabled · Sync failed" })
            assertTrue(nodes(scene).any { text(it) == "Live TV · Movies · Series" })
            assertTrue(nodes(scene).any { text(it) == "Live TV" })
            assertTrue(nodes(scene).any { text(it) == "Default live" })
            assertTrue(nodes(scene).any { text(it) == "The provider catalog could not be refreshed." })
            val shown = nodes(scene).joinToString(" ") { text(it) }
            assertFalse(shown.contains("http", ignoreCase = true), "no server address is rendered")
            assertFalse(shown.contains("password", ignoreCase = true) && shown.contains("fixture"), "no credential is rendered")
        } finally { scene.close() }
    }

    @Test fun editOpensTheSheetWithTheNameAndScopesButNoCredentialFields() {
        val updates = mutableListOf<Triple<Long, String, XtreamScopes>>()
        val scene = scene(390, 1400) { page(compact = true, update = { id, name, scopes -> updates += Triple(id, name, scopes) }) }
        try {
            frames(scene)
            click(scene, "Edit"); frames(scene, 45)
            val fields = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }
            assertEquals(1, fields.size, "editing offers the name only")
            assertTrue(nodes(scene).any { text(it) == "Connection name" })
            assertFalse(nodes(scene).any { text(it) == "Server URL" })
            fields.single().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Living room TV"))
            frames(scene)
            click(scene, "Save connection"); frames(scene, 45)
            assertEquals(listOf(Triple(1L, "Living room TV", XtreamScopes(true, true, true, true))), updates)
        } finally { scene.close() }
    }

    @Test fun addingAConnectionSendsTheTypedLoginWithTheChosenScopes() {
        val created = mutableListOf<List<String>>()
        val scene = scene(390, 1400) { page(compact = true, create = { name, url, username, password, _ ->
            created += listOf(name, url, username, password) }) }
        try {
            frames(scene)
            click(scene, "Add an Xtream connection"); frames(scene, 45)
            val fields = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }
            assertEquals(4, fields.size, "name, server address, username and password")
            for (label in listOf("Connection name", "Server URL", "Username", "Password"))
                assertTrue(nodes(scene).any { text(it) == label }, "$label is offered")
            assertTrue(actions(scene, "Add connection").single().config.contains(SemanticsProperties.Disabled),
                "the form cannot be submitted empty")
            listOf("Garage", "https://garage.example:8080", "viewer", "fixture-password").forEachIndexed { index, value ->
                nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }[index]
                    .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))
                frames(scene)
            }
            click(scene, "Add connection"); frames(scene, 45)
            assertEquals(listOf(listOf("Garage", "https://garage.example:8080", "viewer", "fixture-password")), created)
        } finally { scene.close() }
    }

    @Test fun removingAConnectionAsksFirst() {
        val removed = mutableListOf<Long>()
        val scene = scene(390, 1400) { page(compact = true, remove = { removed += it }) }
        try {
            frames(scene)
            invoke(actions(scene, "Remove")[1]); frames(scene, 32)
            assertTrue(nodes(scene).any { text(it) == "Remove Bedroom?" })
            click(scene, "Cancel"); frames(scene, 32)
            assertTrue(removed.isEmpty())
            invoke(actions(scene, "Remove")[1]); frames(scene, 32)
            invoke(actions(scene, "Remove").last()); frames(scene, 32)
            assertEquals(listOf(2L), removed)
        } finally { scene.close() }
    }

    @Test fun aWideLayoutReplacesThePasswordInADialogThatNeverAsksForTheAddressAgain() {
        val renewed = mutableListOf<Pair<Long, String>>()
        val scene = scene(1440, 900) { page(compact = false, replacePassword = { id, password -> renewed += id to password }) }
        try {
            frames(scene)
            invoke(actions(scene, "Replace password")[0]); frames(scene, 32)
            assertTrue(nodes(scene).any { text(it) == "New password" })
            assertFalse(nodes(scene).any { text(it) == "Server URL" })
            assertFalse(nodes(scene).any { text(it) == "Username" })
            nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("replacement-password"))
            frames(scene)
            invoke(actions(scene, "Replace password").last()); frames(scene, 32)
            assertEquals(listOf(1L to "replacement-password"), renewed)
        } finally { scene.close() }
    }

    @Test fun aConnectionThatNeedsMigrationKeepsItsEditActionsDisabled() {
        val scene = scene(390, 900) {
            XtreamConnectionsSettings(listOf(XtreamConnection(4, "Legacy", XtreamScopes(), false, XtreamRefresh("idle", null))),
                null, false, null, null, canManage = true, manageNote = "", compact = true, back = {}, request = {},
                create = { _, _, _, _, _ -> }, update = { _, _, _ -> }, replacePassword = { _, _ -> }, sync = {}, setDefault = {}, remove = {})
        }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Operator migration required before this connection can be changed." })
            for (label in listOf("Edit", "Replace password", "Sync now", "Remove"))
                assertTrue(actions(scene, label).all { it.config.contains(SemanticsProperties.Disabled) }, "$label stays disabled")
        } finally { scene.close() }
    }

    @Composable
    private fun page(compact: Boolean, create: (String, String, String, String, XtreamScopes) -> Unit = { _, _, _, _, _ -> },
        update: (Long, String, XtreamScopes) -> Unit = { _, _, _ -> }, replacePassword: (Long, String) -> Unit = { _, _ -> },
        remove: (Long) -> Unit = {}) {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
            XtreamConnectionsSettings(connections, 1L, false, null, null, canManage = true, manageNote = "",
                compact = compact, back = {}, request = {}, create = create, update = update,
                replacePassword = replacePassword, sync = {}, setDefault = {}, remove = remove)
        }
    }

    private fun scene(width: Int, height: Int, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme { content() } }
    private fun frames(scene: ImageComposeScene, count: Int = 24) =
        repeat(count) { frameTime += 16_666_667L; scene.render(frameTime).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun actions(scene: ImageComposeScene, label: String) = nodes(scene).filter { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun invoke(node: SemanticsNode) {
        assertFalse(node.config.contains(SemanticsProperties.Disabled))
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.height > 0f }
            ?: fail("no clickable '$label'")
        invoke(node)
    }
}
