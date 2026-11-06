package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.ParentProfileControls
import org.viptv.app.connection.ProfileManagement
import org.viptv.app.features.settings.TvParentControlsSettings
import org.viptv.app.features.settings.TvSettingsScreen
import org.viptv.app.features.settings.TvSettingsSection
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

// The three parental dialogs are the TV panel (app#907's TvDialogPanel), not the phone's centred Material card.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvParentalDialogsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val calls = mutableListOf<String>()
    private val controls = mapOf(
        "1" to ParentProfileControls(parentKids(false, 12), false),
        "2" to ParentProfileControls(parentKids(true, 10, "PG-13"), false))

    private inner class Panel(snapshot: ProfileManagement, width: Int = 1920, height: Int = 1080,
        controls: Map<String, ParentProfileControls> = this@TvParentalDialogsTest.controls) : AutoCloseable {
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null, notice = null,
                    install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.Parental,
                    parental = { first, sidebar, scale -> TvParentControlsSettings(snapshot, controls, false, null, null, 0,
                        {}, { pin -> calls += "unlock:$pin" }, { current, next -> calls += "pin:$current:$next" },
                        { id, policy -> calls += "policy:$id:${policy.max_age}:${policy.movie_rating}:${policy.series_rating}:${policy.allow_unrated}" },
                        { id, current, next -> calls += "leave:$id:$current:$next" }, {}, first, sidebar, scale) })
            } }
        }

        fun owners() = scene.semanticsOwners.toList()
        fun nodes(): List<SemanticsNode> = owners().flatMap { it.getAllSemanticsNodes(true) }
        fun dialog(): List<SemanticsNode> = owners().drop(1).flatMap { it.getAllSemanticsNodes(true) }
        fun dialogOpen() = owners().size > 1
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun dialogTexts() = dialog().map(::text)
        fun title() = dialog().firstOrNull { text(it).isNotEmpty() && !it.config.contains(SemanticsActions.OnClick) }
        fun focusedText() = (dialog().ifEmpty { nodes() })
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text).orEmpty()
        fun action(label: String) = dialog().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        fun click(label: String) {
            val node = nodes().firstOrNull { text(it).startsWith(label) && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$label' in ${nodes().map(::text).filter(String::isNotBlank)}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun clickDialog(label: String) {
            val node = action(label) ?: fail("no dialog control '$label' in ${dialogTexts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun type(index: Int, value: String) {
            val field = dialog().filter { it.config.contains(SemanticsActions.SetText) }.getOrNull(index)
                ?: fail("no dialog field $index in ${dialogTexts()}")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames()
        }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
        }
        suspend fun settle() = repeat(24) { scene.render(++frame * 16_666_667L).close(); delay(2) }
        fun frames() = repeat(16) { scene.render(++frame * 16_666_667L).close() }
        fun capture(name: String) {
            System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$name.png").writeBytes(it.bytes) } }
            }
        }
        override fun close() = scene.close()
    }

    @Test fun theParentPinDialogsDrawTheTvPanelAndKeepTheRemoteInside() = runBlocking {
        Panel(parentFixture(kids = true, leavePin = true)).use { panel ->
            panel.settle()
            panel.click("Change parent PIN")
            panel.settle()
            val title = requireNotNull(panel.title())
            assertEquals("Change parent PIN", panel.text(title))
            assertTrue(title.boundsInRoot.height >= 44f, "the title is T type (height ${title.boundsInRoot.height})")
            val cancel = requireNotNull(panel.action("Cancel"))
            assertTrue(cancel.boundsInRoot.height >= 66f, "the actions are T buttons (height ${cancel.boundsInRoot.height})")
            assertTrue(panel.focusedText().contains("Current PIN"), "the remote lands on the current PIN, was '${panel.focusedText()}'")
            val inside = panel.dialogTexts().filter(String::isNotBlank).toSet()
            repeat(8) {
                panel.key(Key.DirectionDown)
                val now = panel.focusedText()
                assertTrue(inside.any { it == now }, "Down keeps focus in the panel, reached '$now'")
            }
            panel.key(Key.Escape)
            assertFalse(panel.dialogOpen(), "Back closes the panel")
        }
    }

    @Test fun changingTheParentPinSavesTheTypedPins() = runBlocking {
        Panel(parentFixture(kids = true, leavePin = true)).use { panel ->
            panel.settle()
            panel.click("Change parent PIN")
            panel.settle()
            panel.type(0, "1234")
            panel.type(1, "5678")
            panel.type(2, "5678")
            panel.clickDialog("Save PIN")
            assertEquals(listOf("pin:1234:5678"), calls)
        }
    }

    @Test fun unlockingAndRemovingAreOnTheSamePanel() = runBlocking {
        Panel(parentFixture(kids = true, leavePin = true, unlocked = false, restricted = true)).use { panel ->
            panel.settle()
            panel.click("Unlock parental controls")
            panel.settle()
            assertEquals("Unlock parental controls", panel.text(requireNotNull(panel.title())))
            panel.type(0, "4321")
            panel.clickDialog("Unlock")
            assertEquals(listOf("unlock:4321"), calls)
        }
        calls.clear()
        Panel(parentFixture(kids = false, leavePin = true)).use { panel ->
            panel.settle()
            panel.click("Remove parent PIN")
            panel.settle()
            assertEquals("Remove parent PIN?", panel.text(requireNotNull(panel.title())))
            assertEquals("Cancel", panel.focusedText(), "a destructive panel opens on Cancel")
            panel.key(Key.DirectionDown)
            assertEquals("Remove PIN", panel.focusedText())
        }
    }

    @Test fun theRestrictionsDialogWalksItsControlsAndSavesThePolicy() = runBlocking {
        Panel(parentFixture(kids = true, leavePin = true)).use { panel ->
            panel.settle()
            panel.click("Child")
            panel.settle()
            assertEquals("Restrictions for Child", panel.text(requireNotNull(panel.title())))
            assertTrue(panel.focusedText().startsWith("Kids restrictions"), "the remote lands on the toggle, was '${panel.focusedText()}'")
            val seen = mutableListOf<String>()
            repeat(20) {
                seen += panel.focusedText()
                panel.key(Key.DirectionDown)
            }
            assertTrue(seen.any { it == "PG-13" }, "the server's movie labels are reachable: $seen")
            assertTrue(seen.any { it == "TV-14" }, "the server's series labels are reachable: $seen")
            assertTrue(seen.any { it.startsWith("Allow titles with no rating") }, "the unrated toggle is reachable: $seen")
            assertTrue(seen.all { label -> panel.dialogTexts().any { it == label } }, "no page row takes focus: $seen")
            panel.clickDialog("TV-14")
            panel.clickDialog("Save restrictions")
            assertEquals(listOf("policy:2:10:PG-13:TV-14:false"), calls, "the policy keeps the movie limit and saves the picked series one")
        }
    }

    @Test fun theLeavePinDialogSavesAndOffersItsRemoval() = runBlocking {
        Panel(parentFixture(kids = true, leavePin = true)).use { panel ->
            panel.settle()
            panel.click("Set leave PIN")
            panel.settle()
            assertEquals("Set Parent’s leave PIN", panel.text(requireNotNull(panel.title())))
            panel.type(0, "2222")
            panel.type(1, "2222")
            panel.clickDialog("Save PIN")
            assertEquals(listOf("leave:1::2222"), calls)
        }
        calls.clear()
        val withPin = controls + mapOf("1" to ParentProfileControls(parentKids(false, 12), true))
        Panel(parentFixture(kids = true, leavePin = true), controls = withPin).use { panel ->
            panel.settle()
            panel.click("Change leave PIN")
            panel.settle()
            assertEquals("Change Parent’s leave PIN", panel.text(requireNotNull(panel.title())))
            val remove = requireNotNull(panel.action("Remove PIN"))
            assertTrue(remove.config.contains(SemanticsProperties.Disabled), "Remove PIN waits for the current PIN")
            panel.type(0, "1111")
            panel.clickDialog("Remove PIN")
            assertEquals(listOf("leave:1:1111:"), calls)
        }
    }

    @Test fun thePanelsRenderAt1920And1024() = runBlocking {
        for ((width, height) in listOf(1920 to 1080, 1024 to 768)) {
            Panel(parentFixture(kids = true, leavePin = true), width, height).use { panel ->
                panel.settle()
                panel.click("Change parent PIN")
                panel.settle()
                panel.capture("tv-parent-pin-$width")
                panel.key(Key.Escape)
                panel.click("Child")
                panel.settle()
                panel.capture("tv-restrictions-$width")
                panel.key(Key.Escape)
                panel.click("Set leave PIN")
                panel.settle()
                panel.capture("tv-leave-pin-$width")
            }
        }
    }
}
