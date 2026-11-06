package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.ParentProfileControls
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.ParentControlsSettings
import org.viptv.app.features.settings.ProfilePageActions
import org.viptv.app.features.settings.ProfilePageValues
import org.viptv.app.features.settings.ProfileSettingsPage
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.features.settings.TvProfileSettingsPage
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class KidsPolicyReadoutTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val calls = mutableListOf<String>()
    private var frame = 0L
    private val profile = Profile("2", "Child", null)

    private fun actions(saved: androidx.compose.runtime.MutableState<Long>) = ProfilePageActions(
        kids = { enabled, pin -> calls += "kids:$enabled:$pin"; saved.value += 1 },
        maturity = { movie, series, unrated, pin -> calls += "maturity:$movie:$series:$unrated:$pin"; saved.value += 1 },
        approvedTitles = { calls += "approved titles" },
        parentalControls = { calls += "parental controls" })

    private fun locked(readable: Boolean) = ProfilePageValues(profile = profile, local = false, kids = true, maxAge = 10,
        approvedTitles = null, householdPinConfigured = true, householdUnlocked = false,
        policy = if (readable) parentKids(true, 10, "PG-13", "TV-14") else null)

    private fun scene(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private fun render(scene: ImageComposeScene) = repeat(12) { scene.render(++frame * 16_666_667L).close() }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun texts(scene: ImageComposeScene): List<String> = nodes(scene).mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
    }

    private fun assertNoDefaults(scene: ImageComposeScene, surface: String) {
        val shown = texts(scene)
        assertFalse(shown.any { it.contains("All ratings") }, "$surface prints All ratings for a limit it never read")
        assertFalse(shown.any { it.contains("—") }, "$surface prints an unknown approved count as a dash")
        assertTrue(shown.count { it.contains("Unavailable") } >= 2, "$surface names the unread limits: $shown")
    }

    @Test fun everyProfileLayoutNamesTheUnreadLimitsInsteadOfPrintingDefaults() {
        for (layout in listOf(SettingsLayout.Phone, SettingsLayout.Tablet, SettingsLayout.Desktop)) {
            val saved = mutableStateOf(0L)
            val scene = scene(if (layout == SettingsLayout.Phone) 390 else 1280, 900) {
                ProfileSettingsPage(locked(readable = false), false, null, saved.value, null, actions(saved), layout)
            }
            try {
                render(scene)
                assertNoDefaults(scene, "Profile ($layout)")
                assertTrue(texts(scene).any { it.contains("Enter the parent PIN to manage this household.") },
                    "$layout names the reason")
                val maturity = nodes(scene).single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Maturity limit" } == true &&
                        it.config.contains(SemanticsActions.OnClick)
                }
                maturity.config[SemanticsActions.OnClick].action!!.invoke(); render(scene)
                assertEquals(listOf("parental controls"), calls, "$layout sends a locked limit to the household PIN")
                calls.clear()
            } finally { scene.close() }
        }
    }

    @Test fun theTvProfilePageNamesTheUnreadLimitsInsteadOfPrintingDefaults() {
        val saved = mutableStateOf(0L)
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvProfileSettingsPage(locked(readable = false), false, null, saved.value, null, actions(saved), 1f,
                    FocusRequester(), FocusRequester()) { calls += "parental controls" }
            } }
        }
        try {
            render(scene)
            assertNoDefaults(scene, "TV Profile")
            assertTrue(texts(scene).any { it.contains("Enter the parent PIN to manage this household.") })
        } finally { scene.close() }
    }

    @Test fun theParentalControlsPageOnlyOffersTheEditorItCouldRead() {
        val scene = scene(1280, 900) {
            ParentControlsSettings(parentFixture(kids = true), mapOf(
                "1" to ParentProfileControls(parentKids(false, 12)),
                "2" to ParentProfileControls(null)), false, null, null, 0L,
                {}, {}, { _, _ -> calls += "restrictions" }, { _, _, _ -> }, {})
        }
        try {
            render(scene)
            val shown = texts(scene)
            assertTrue(shown.any { it.contains("Kids profile · limits unavailable") },
                "a profile whose policy did not load says so: $shown")
            assertFalse(shown.any { it.startsWith("Kids profile · movies") })
            val edits = nodes(scene).filter {
                it.config.contains(SemanticsActions.OnClick) &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Edit restrictions" } == true
            }
            assertEquals(2, edits.size)
            assertEquals(1, edits.count { it.config.contains(SemanticsProperties.Disabled) },
                "only the profile whose policy was read offers its editor")
            assertEquals(1, edits.count { !it.config.contains(SemanticsProperties.Disabled) })
        } finally { scene.close() }
    }
}