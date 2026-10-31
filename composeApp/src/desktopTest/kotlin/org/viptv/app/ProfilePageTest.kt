package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.Profile
import org.viptv.app.connection.SimklLinkStatus
import org.viptv.app.features.settings.LinkedServicesSettings
import org.viptv.app.features.settings.ProfilePageActions
import org.viptv.app.features.settings.ProfilePageValues
import org.viptv.app.features.settings.ProfileSettingsPage
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.TvProfileSettingsPage
import org.viptv.app.features.settings.TvSettingsPreferences
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProfilePageTest {
    @get:Rule val uiThread = UiThreadRule()

    private val calls = mutableListOf<String>()
    private var frame = 0L
    private var focusManager: FocusManager? = null

    private fun profile(id: String = "1", name: String = "Viewer", locked: Boolean = false, canDelete: Boolean = true) =
        Profile(id, name, null, locked = locked, canDelete = canDelete)

    private fun actions(saved: androidx.compose.runtime.MutableState<Long>) = ProfilePageActions(
        rename = { name, pin -> calls += "rename:$name:$pin" },
        avatar = { pick, _ -> calls += "avatar:${pick.style}:${pick.choice}" },
        kids = { enabled, pin -> calls += "kids:$enabled:$pin"; saved.value += 1 },
        maturity = { age, pin -> calls += "maturity:$age:$pin"; saved.value += 1 },
        approvedTitles = { calls += "approved titles" },
        lock = { old, next -> calls += "lock:$old:$next"; saved.value += 1 },
        parentalControls = { calls += "parental controls" },
        language = { calls += "language" },
        delete = { pin -> calls += "delete:$pin"; saved.value += 1 })

    private inner class Page(values: ProfilePageValues) : AutoCloseable {
        val saved = mutableStateOf(0L)
        val scene = ImageComposeScene(1000, 1400, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ProfileSettingsPage(values, false, null, saved.value, actions(saved), SettingsLayout.Tablet)
            } }
        }

        fun render() = repeat(12) { scene.render(++frame * 16_666_667L).close(); Thread.sleep(3) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun shows(label: String) = nodes().any { node ->
            values(node).any { it == label || it.startsWith("$label ") }
        }
        fun click(label: String) {
            click(nodes(), label)
            render()
        }
        fun clickLast(label: String) {
            val matches = nodes().filter { text(it) == label && it.config.contains(SemanticsActions.OnClick) }.reversed()
            for (node in matches) {
                node.config[SemanticsActions.OnClick].action!!.invoke()
                render()
                if (calls.isNotEmpty()) return
            }
            fail("no '$label' changed anything")
        }
        fun type(value: String) {
            val field = nodes().firstOrNull { it.config.contains(SemanticsActions.SetText) } ?: fail("no text field")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); render()
        }
        override fun close() = scene.close()
    }

    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private fun values(node: SemanticsNode): List<String> = buildList {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
    }

    private fun click(nodes: List<SemanticsNode>, label: String) {
        val node = nodes.firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            ?: nodes.firstOrNull { text(it).startsWith("$label ") && it.config.contains(SemanticsActions.OnClick) }
            ?: fail("no clickable '$label' in ${nodes.map(::text).filter(String::isNotBlank)}")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }

    @Test fun renamingSavesAndAFailedSaveKeepsTheDialogOpenWithTheTypedValue() {
        Page(ProfilePageValues(profile = profile(), local = false)).use { page ->
            page.render()
            assertTrue(page.shows("Profile") && page.shows("Viewer"), "the page names the profile it edits")
            page.click("Name")
            page.type("Renamed")
            page.click("Save")
            assertEquals(listOf("rename:Renamed:"), calls)
            assertTrue(page.shows("Renamed"), "a save that has not landed yet keeps the typed value in the field")
            page.saved.value = 1
            page.render()
            assertFalse(page.nodes().any { text(it) == "Save" }, "a save that lands closes the dialog")
        }
    }

    @Test fun theAvatarRowOpensTheExistingPicker() {
        Page(ProfilePageValues(profile = profile(), local = false)).use { page ->
            page.render()
            page.click("V Avatar")
            assertTrue(page.shows("Choose an avatar"))
        }
    }

    @Test fun turningKidsOnAsksForTheHouseholdPinWhileTheSessionHasNotEnteredIt() {
        Page(ProfilePageValues(profile = profile(), local = false, kids = false, maxAge = null,
            householdPinConfigured = true, householdUnlocked = false)).use { page ->
            page.render()
            page.click("Kids profile")
            assertTrue(page.shows("Turn on Kids profile"))
            page.type("4821")
            page.click("Continue")
            assertEquals(listOf("kids:true:4821"), calls)
        }
        calls.clear()
        Page(ProfilePageValues(profile = profile(), local = false, kids = false, householdPinConfigured = false)).use { page ->
            page.render()
            page.click("Kids profile")
            assertEquals(listOf("kids:true:"), calls, "a session that already unlocked the limit saves without a prompt")
        }
    }

    @Test fun turningKidsOffSavesWithoutAPin() {
        Page(ProfilePageValues(profile = profile(), local = false, kids = true, maxAge = 13,
            householdPinConfigured = true, householdUnlocked = false)).use { page ->
            page.render()
            page.click("Kids profile")
            assertEquals(listOf("kids:false:"), calls)
        }
    }

    @Test fun theMaturityRowWritesTheRatingAsItsAge() {
        Page(ProfilePageValues(profile = profile(), local = false, kids = true, maxAge = null, approvedTitles = 2)).use { page ->
            page.render()
            assertTrue(page.shows("Maturity limit"), "the row appears while Kids profile is on")
            page.click("Maturity limit")
            page.click("PG-13")
            page.click("Save")
            assertEquals(listOf("maturity:13:"), calls)
        }
    }

    @Test fun approvedTitlesShowsItsCountAndOpensOnlyWhileKidsIsOn() {
        Page(ProfilePageValues(profile = profile(), local = false, kids = true, maxAge = 13, approvedTitles = 3)).use { page ->
            page.render()
            assertTrue(page.shows("Approved titles"))
            assertTrue(page.shows("3"))
            page.click("Approved titles")
            assertEquals(listOf("approved titles"), calls)
        }
        calls.clear()
        Page(ProfilePageValues(profile = profile(), local = false, kids = false, approvedTitles = 3)).use { page ->
            page.render()
            assertFalse(page.shows("Approved titles"), "approved titles only shows in the kids library")
        }
    }

    @Test fun localBuildsOfferTheProfilePinAndNoServerRow() {
        Page(ProfilePageValues(profile = profile(locked = false), local = true)).use { page ->
            page.render()
            for (row in listOf("Kids profile", "Maturity limit", "Approved titles")) assertFalse(page.shows(row), "$row needs a server")
            page.click("Profile lock")
            assertTrue(page.shows("Set profile PIN"))
            page.type("4321")
            page.click("Save PIN")
            assertEquals(listOf("lock::4321"), calls)
        }
    }

    @Test fun serverAccountsSendTheProfileLockRowToParentalControls() {
        Page(ProfilePageValues(profile = profile(), local = false)).use { page ->
            page.render()
            page.click("Profile lock")
            assertEquals(listOf("parental controls"), calls)
        }
    }

    @Test fun languageSummarisesTheProfileAudioAndSubtitlesAndOpensTheirPage() {
        Page(ProfilePageValues(profile = profile(), local = false, audioLanguage = "en", subtitleLanguage = "pt-BR")).use { page ->
            page.render()
            assertTrue(page.shows("en · pt-BR"))
            page.click("Language")
            assertEquals(listOf("language"), calls)
        }
        calls.clear()
        Page(ProfilePageValues(profile = profile(), local = false)).use { page ->
            page.render()
            assertTrue(page.shows("Original language"), "an unset language reads as the original")
        }
    }

    @Test fun deleteIsAbsentOnTheOnlyProfileAndAsksBeforeRemovingASecond() {
        Page(ProfilePageValues(profile = profile(canDelete = false), local = false, canDelete = false)).use { page ->
            page.render()
            assertFalse(page.shows("Delete profile"), "the account's only profile has no delete row")
        }
        Page(ProfilePageValues(profile = profile(canDelete = true), local = false)).use { page ->
            page.render()
            page.click("Delete profile")
            assertTrue(page.shows("Delete Viewer?"))
            assertTrue(page.shows("This permanently removes this profile’s watch history, favorites and preferences."))
            page.clickLast("Delete profile")
            assertEquals(listOf("delete:"), calls)
        }
    }

    @Test fun linkedServicesShowsTheSelectedProfilesSimklStateAndConfirmsUnlink() {
        var loads = 0
        val scene = ImageComposeScene(1000, 800, Density(1f)) { ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            LinkedServicesSettings(SimklLinkStatus(configured = true, linked = false), "Viewer", compact = false, busy = false,
                error = null, load = { loads += 1 }, link = { calls += "link" }, unlink = { calls += "unlink" })
        } } }
        try {
            render(scene)
            assertEquals(1, loads, "the page reads the profile's link state when it opens")
            click(nodes(scene), "Link Simkl"); render(scene)
            assertEquals(listOf("link"), calls)
        } finally { scene.close() }
        calls.clear()
        val linked = ImageComposeScene(1000, 800, Density(1f)) { ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            LinkedServicesSettings(SimklLinkStatus(configured = true, linked = true), "Viewer", compact = false, busy = false,
                error = null, load = { }, link = { }, unlink = { calls += "unlink" })
        } } }
        try {
            render(linked)
            assertTrue(nodes(linked).any { values(it).any { value -> value == "Linked to this profile" } })
            click(nodes(linked), "Unlink Simkl"); render(linked)
            click(nodes(linked), "Unlink"); render(linked)
            assertEquals(listOf("unlink"), calls)
        } finally { linked.close() }
    }

    @Test fun theRemoteReachesEveryRowAndLeftReturnsToTheRail() = runBlocking {
        calls.clear()
        val saved = mutableStateOf(0L)
        val values = ProfilePageValues(profile = profile(), local = false, kids = true, maxAge = 13, approvedTitles = 1,
            householdPinConfigured = true)
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, null,
                television = true, initialSection = "Profile", tvPreferences = TvSettingsPreferences(), changeTvPreferences = { },
                tvProfilePage = { first, sidebar, scale, _ ->
                    TvProfileSettingsPage(values, false, null, saved.value, actions(saved), scale, first, sidebar) { }
                })
        } }
        try {
            frames(scene)
            assertFocused(scene, "Name")
            for (row in listOf("Avatar", "Kids profile", "Maturity limit", "Approved titles", "Profile lock", "Language")) {
                press(scene, FocusDirection.Down)
                assertFocused(scene, row)
            }
            press(scene, FocusDirection.Left)
            frames(scene)
            val rail = focused(scene)
            assertNotNull(rail)
            assertTrue(rail.boundsInRoot.left < 560f, "Left from a profile row returns to the sidebar")
            assertTrue(text(rail).startsWith("Profile"), "Left lands on the rail row that opened the page, was '${text(rail)}'")
        } finally { scene.close() }
    }

    private fun render(scene: ImageComposeScene) {
        repeat(12) { scene.render(++frame * 16_666_667L).close(); Thread.sleep(3) }
    }

    private suspend fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(++frame * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene)
        val now = focused(scene)?.let(::text).orEmpty()
        assertTrue(now.startsWith(label), "expected focus on $label, was '$now'")
    }
    private suspend fun press(scene: ImageComposeScene, direction: FocusDirection) {
        focusManager!!.moveFocus(direction)
        frames(scene)
    }
}