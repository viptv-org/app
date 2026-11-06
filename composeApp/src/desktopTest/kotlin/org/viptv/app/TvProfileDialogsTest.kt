package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.KidsRatings
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RatingLabel
import org.viptv.app.features.entry.TvProfileEditAction
import org.viptv.app.features.entry.TvProfileEditDialog
import org.viptv.app.features.settings.ProfilePageActions
import org.viptv.app.features.settings.ProfilePageValues
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.TvProfileSettingsPage
import org.viptv.app.features.settings.TvSettingsPreferences
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

// The four profile dialogs on TV are the panel the design boards draw, not the phone's centred card: T sizes, and the remote lands on a control of the dialog.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvProfileDialogsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val calls = mutableListOf<String>()
    private var frame = 0L

    private val ratings = KidsRatings(
        movie = listOf(RatingLabel("G"), RatingLabel("PG"), RatingLabel("PG-13"), RatingLabel("R"), RatingLabel("NC-17")),
        series = listOf(RatingLabel("TV-Y"), RatingLabel("TV-Y7"), RatingLabel("TV-G"), RatingLabel("TV-PG"), RatingLabel("TV-14"), RatingLabel("TV-MA")))

    private fun profile() = Profile("1", "Viewer", null, locked = false)

    private fun actions(saved: androidx.compose.runtime.MutableState<Long>) = ProfilePageActions(
        rename = { name, pin -> calls += "rename:$name:$pin" },
        kids = { enabled, pin -> calls += "kids:$enabled:$pin"; saved.value += 1 },
        maturity = { movie, series, unrated, pin -> calls += "maturity:$movie:$series:$unrated:$pin"; saved.value += 1 },
        lock = { old, next -> calls += "lock:$old:$next"; saved.value += 1 },
        delete = { pin -> calls += "delete:$pin"; saved.value += 1 })

    private fun values(householdLocked: Boolean = false) = ProfilePageValues(profile = profile(), local = false,
        kids = true, maxAge = 10, approvedTitles = 2, householdPinConfigured = householdLocked, householdUnlocked = false,
        policy = KidsPolicy(enabled = true, max_age = 10, movie_rating = "PG", series_rating = "TV-PG", ratings = ratings))

    private inner class Page(page: ProfilePageValues, width: Int = 1920, height: Int = 1080) : AutoCloseable {
        val saved = mutableStateOf(0L)
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(true) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, null,
                        television = true, initialSection = "Profile",
                        tvPreferences = TvSettingsPreferences(), changeTvPreferences = { _, _ -> },
                        tvProfilePage = { first, sidebar, scale, _ ->
                            TvProfileSettingsPage(page, false, null, saved.value, null, actions(saved), scale, first, sidebar) { }
                        })
                }
            }
        }

        val pageOwners = 1

        fun render(count: Int = 16) = repeat(count) { scene.render(++frame * 16_666_667L).close() }
        fun owners(): List<SemanticsOwner> = scene.semanticsOwners.toList()
        fun nodes(): List<SemanticsNode> = owners().flatMap { it.getAllSemanticsNodes(true) }
        fun dialog(): List<SemanticsNode> = owners().drop(pageOwners).flatMap { it.getAllSemanticsNodes(true) }
        fun dialogOpen() = owners().size > pageOwners
        // A Popup draws in its own semantics owner, which keeps the focus state the page had before it opened.
        fun focused(): SemanticsNode? = (dialog().ifEmpty { nodes() })
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun focusedText() = focused()?.let(::text).orEmpty()
        fun dialogTitle() = dialog().firstOrNull { text(it).isNotEmpty() && !it.config.contains(SemanticsActions.OnClick) }
        fun action(label: String) = dialog().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        fun click(label: String) {
            val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$label' in ${nodes().map(::text).filter(String::isNotBlank)}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        fun clickDialog(label: String) {
            val node = action(label) ?: fail("no dialog control '$label'")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        fun type(value: String) {
            val field = dialog().firstOrNull { it.config.contains(SemanticsActions.SetText) } ?: fail("no dialog text field")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); render()
        }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            render()
        }
        fun capture(name: String) {
            System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$name.png").writeBytes(it.bytes) } }
            }
        }
        override fun close() = scene.close()
    }

    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private suspend fun Page.settle(count: Int = 16) {
        repeat(count) { scene.render(++frame * 16_666_667L).close(); delay(2) }
    }

    private suspend fun Page.open(row: String) {
        settle()
        val node = nodes().firstOrNull { text(it).startsWith(row) && it.config.contains(SemanticsActions.OnClick) }
            ?: fail("no page row '$row' in ${nodes().map(::text).filter(String::isNotBlank)}")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        settle(24)
    }

    @Test fun everyProfileDialogDrawsTheTvPanelAndTheRemoteLandsInsideIt() = runBlocking {
        Page(values()).use { page ->
            page.open("Name")
            assertTrue(page.dialogOpen())
            val title = requireNotNull(page.dialogTitle())
            assertTrue(title.boundsInRoot.height >= 44f, "the title is T type, not the phone dialog's 15 sp (height ${title.boundsInRoot.height})")
            assertTrue(page.dialog().any { text(it) == "Cancel" }, "the actions are the panel's rows")
            val cancel = requireNotNull(page.action("Cancel"))
            assertTrue(cancel.boundsInRoot.height >= 66f, "actions are T buttons (height ${cancel.boundsInRoot.height})")
            assertTrue(page.dialog().any { it.config.getOrNull(SemanticsProperties.Focused) == true },
                "the remote lands in the dialog, not on the row behind it")
            page.key(Key.Escape)
            assertFalse(page.dialogOpen(), "Back closes the panel")
            assertTrue(page.focusedText().startsWith("Name"), "focus returns to the row that opened it, was '${page.focusedText()}'")
            assertTrue(calls.isEmpty(), "closing without Save changes nothing")
        }
        calls.clear()
        Page(values()).use { page ->
            page.open("Delete profile")
            val title = requireNotNull(page.dialogTitle())
            assertEquals("Delete Viewer?", text(title))
            assertTrue(title.boundsInRoot.height >= 44f)
            assertEquals("Cancel", page.focusedText(), "a destructive dialog opens on Cancel")
        }
    }

    @Test fun renamingFromTheRemoteSavesTheTypedNameAndReturnsToItsRow() = runBlocking {
        Page(values()).use { page ->
            page.open("Name")
            assertEquals(1, page.dialog().count { it.config.contains(SemanticsActions.SetText) })
            page.type("Renamed")
            page.click("Save")
            assertEquals(listOf("rename:Renamed:"), calls)
            assertTrue(page.dialogOpen(), "a save that has not landed keeps the panel open")
            page.saved.value = 1
            page.settle(24)
            assertFalse(page.dialogOpen())
            assertTrue(page.focusedText().startsWith("Name"), "focus returns to the row that opened it, was '${page.focusedText()}'")
        }
    }

    @Test fun theMaturityEditorWalksItsRatingsAndTheRemoteNeverFallsBehindThePanel() = runBlocking {
        Page(values()).use { page ->
            page.open("Maturity limit")
            val inside = page.dialog().mapNotNull { node -> text(node).takeIf(String::isNotBlank) }.toSet()
            val seen = mutableListOf<String>()
            repeat(18) {
                seen += page.focusedText()
                page.key(Key.DirectionDown)
            }
            assertTrue(seen.all { it in inside }, "every focus stop is a control of the dialog; focus reached ${seen.filter { it !in inside }}")
            assertTrue(seen.any { it == "All ratings" }, "the rating pills are reachable: $seen")
            assertTrue(seen.any { it.startsWith("Allow titles with no rating") }, "the unrated toggle is reachable: $seen")
            assertTrue(seen.any { it == "Save" } && seen.any { it == "Cancel" }, "the actions are the last stops: $seen")
            assertFalse(seen.any { it == "Avatar" || it == "Language" }, "the page behind the panel never takes focus: $seen")
        }
    }

    @Test fun theMaturityEditorSavesThroughTheHouseholdPinPanel() = runBlocking {
        Page(values(householdLocked = true)).use { page ->
            page.open("Maturity limit")
            page.clickDialog("PG-13")
            page.clickDialog("Save")
            page.settle(24)
            val title = requireNotNull(page.dialogTitle())
            assertEquals("Change the maturity limit", text(title))
            assertTrue(page.dialog().any { it.config.contains(SemanticsActions.SetText) }, "the household PIN field is offered")
            assertTrue(page.focusedText().contains("Household PIN"), "the PIN field is the focus stop, was '${page.focusedText()}'")
            page.type("4821")
            page.clickDialog("Continue")
            assertEquals(listOf("maturity:PG-13:TV-PG:false:4821"), calls)
        }
    }

    @Test fun deletingRunsTheTvDeletePanelWithThePinField() = runBlocking {
        Page(values()).use { page ->
            page.open("Delete profile")
            assertEquals("Cancel", page.focusedText())
            val delete = requireNotNull(page.action("Delete profile"))
            assertFalse(delete.config.contains(SemanticsProperties.Disabled), "no local PIN is set, so Delete is available")
            page.clickDialog("Delete profile")
            assertEquals(listOf("delete:"), calls)
        }
    }

    @Test fun thePanelsRenderAt1920And1024() = runBlocking {
        for ((width, height) in listOf(1920 to 1080, 1024 to 768)) {
            Page(values(householdLocked = true), width, height).use { page ->
                page.open("Name")
                page.capture("tv-profile-name-$width")
                page.key(Key.Escape)
                page.open("Maturity limit")
                page.capture("tv-maturity-$width")
                page.key(Key.Escape)
                page.open("Maturity limit")
                page.clickDialog("PG-13")
                page.clickDialog("Save")
                page.settle(24)
                page.capture("tv-household-pin-$width")
                page.key(Key.Escape)
                page.open("Delete profile")
                page.capture("tv-delete-profile-$width")
            }
        }
    }

    @Test fun theProfilesEditProfileDialogUsesTheSamePanel() {
        var renamed: Pair<String, String>? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                TvProfileEditDialog(TvProfileEditAction.Name, profile(), false, { },
                    { name, pin -> renamed = name to pin }, { _, _ -> }, {})
            }
        }
        try {
            repeat(24) { scene.render(++frame * 16_666_667L).close() }
            val dialog = scene.semanticsOwners.drop(1).flatMap { it.getAllSemanticsNodes(true) }
            val title = dialog.firstOrNull { text(it) == "Name" && !it.config.contains(SemanticsActions.OnClick) }
            assertNotNull(title, "the edit dialog draws the TV Name panel")
            assertTrue(title.boundsInRoot.height >= 44f)
            val field = dialog.firstOrNull { it.config.contains(SemanticsActions.SetText) }
            assertNotNull(field)
            assertTrue(dialog.any { text(it) == "Cancel" }, "not the phone TextButton row")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Renamed"))
            repeat(8) { scene.render(++frame * 16_666_667L).close() }
            val save = scene.semanticsOwners.drop(1).flatMap { it.getAllSemanticsNodes(true) }
                .firstOrNull { text(it) == "Save" && it.config.contains(SemanticsActions.OnClick) }
            assertNotNull(save)
            assertTrue(save.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("Renamed" to "", renamed)
        } finally { scene.close() }
    }
}