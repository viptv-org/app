package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.*
import org.viptv.app.theme.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class StremioImportUiTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @AfterTest fun close() = scope.cancel()
    private fun session(transport: StremioImportTransport, editable: Boolean = true, restricted: Boolean = false, name: String = "Synthetic profile") =
        StremioImportSession(scope, transport, "2", name, restricted, editable)
    private fun scene(width: Int, height: Int, session: StremioImportSession?, name: String = "Synthetic profile", compact: Boolean = false) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { StremioImportScreen(session, name, compact) } }
        }

    @Test fun guidedBrowserFlowMovesFocusThroughStagesAndNeverRetainsThePassword() {
        val transport = FakeStremioTransport()
        val scene = scene(1000, 1600, session(transport))
        try {
            val probe = Probe(scene); probe.frames(12)
            assertTrue(probe.has("Step 1 of 4: Connect Stremio")); assertTrue(probe.has("Import into: Synthetic profile"))
            assertTrue(probe.has("Preview first. Nothing is saved until you confirm the import."))
            assertEquals(ToggleableState.On, probe.toggleState("My List")); assertEquals(ToggleableState.On, probe.toggleState("Watch history and resume"))
            assertTrue(probe.disabled("Next: Choose add-ons"), "Credentials are required before a preview")
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            assertTrue(probe.field("Stremio password").config.contains(SemanticsProperties.Password))
            probe.clickDescription("Show password"); assertNotNull(probe.described("Hide password"))
            probe.click("Next: Choose add-ons")
            assertEquals(listOf("synthetic-password"), transport.passwords)
            assertTrue(probe.has("Step 2 of 4: Choose add-ons"))
            assertTrue(probe.focused("Choose add-ons"), "The stage heading receives focus")
            assertNull(probe.fieldOrNull("Stremio password"))
            assertEquals(ToggleableState.On, probe.describedState("Use add-on Synthetic metadata"))
            assertEquals(ToggleableState.Off, probe.describedState("Use add-on Broken"))
            assertTrue(probe.described("Use add-on Broken")!!.config.contains(SemanticsProperties.Disabled))
            assertTrue(probe.has("This add-on uses an unsupported connection method."))
            probe.capture("browser-addons")
            probe.click("Next: Review items")
            assertTrue(probe.focused("Review items"))
            assertEquals(ToggleableState.On, probe.describedState("Include Saved movie"))
            assertEquals(ToggleableState.On, probe.describedState("Include Episode resume season 1 episode 2"))
            probe.clickDescription("Include Saved movie")
            assertTrue(probe.has("1 review items included · 1 excluded · 2 shown in this preview"))
            probe.click("Next: Confirm import")
            assertTrue(probe.focused("Confirm import"))
            assertEquals(ToggleableState.Off, probe.toggleState("I confirm this import is for Synthetic profile."))
            assertTrue(probe.disabled("Confirm import", button = true))
            probe.click("I confirm this import is for Synthetic profile.")
            probe.capture("browser-confirm")
            probe.click("Confirm import", button = true)
            assertTrue(probe.has("Import complete")); assertTrue(probe.has("The confirmed changes were saved to this profile."))
            assertEquals("apply:$stremioHandle:[$stremioMovieId]:1", transport.calls.last())
            probe.click("Start another preview")
            assertEquals("", probe.editable("Stremio email")); assertEquals("", probe.editable("Stremio password"))
            assertTrue(probe.has("Step 1 of 4: Connect Stremio"))
        } finally { scene.close() }
    }

    @Test fun passwordIsClearedOnFailureAndCancelShowingOnlyFixedCopy(): Unit = runBlocking {
        val transport = FakeStremioTransport()
        transport.preview = { throw StremioImportFailure(401, "stremio_credentials_invalid") }
        val scene = scene(1000, 1200, session(transport))
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            probe.click("Next: Choose add-ons")
            assertTrue(probe.has("Stremio sign-in failed. Check your details."))
            assertEquals("", probe.editable("Stremio password"))
            assertEquals("synthetic@example.invalid", probe.editable("Stremio email"))
            val gate = CompletableDeferred<StremioPreview>()
            transport.preview = { gate.await() }
            probe.setText("Stremio password", "second-password")
            probe.click("Next: Choose add-ons")
            assertTrue(probe.has("Reading Stremio for a read-only preview…"))
            assertEquals("", probe.editable("Stremio password"), "Cleared as soon as the request starts")
            probe.click("Cancel")
            assertFalse(probe.has("Reading Stremio for a read-only preview…"))
            assertEquals("", probe.editable("Stremio email"))
            gate.complete(stremioAddons()); probe.frames(6)
            assertTrue(probe.has("Step 1 of 4: Connect Stremio"), "A cancelled preview cannot resume the wizard")
        } finally { scene.close() }
    }

    @Test fun nativeDeviceSessionShowsAddonsWithoutChoicesAndPointsToTheWeb() {
        val transport = FakeStremioTransport()
        transport.review = { _, _ -> stremioReview(addonsToAdd = 0) }
        val scene = scene(390, 1600, session(transport, editable = false), compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            probe.click("Next: Choose add-ons")
            assertTrue(probe.has("Step 2 of 4: Choose add-ons"))
            assertTrue(probe.has("Add-ons are account-wide. You can add them from VIPTV on the web."))
            assertTrue(probe.has("Synthetic metadata")); assertTrue(probe.has("New — not added from this device"))
            assertNull(probe.described("Use add-on Synthetic metadata"), "No add-on checkboxes on a device session")
            listOf("Select all", "Clear all", "Continue without add-ons").forEach { assertFalse(probe.has(it), it) }
            probe.capture("native-addons")
            probe.click("Next: Review items")
            assertEquals("review:$stremioHandle:[]:0", transport.calls.last())
            assertTrue(probe.has("Review items"))
            assertFalse(probe.nodes().any { probe.text(it).startsWith("New add-ons to register") })
        } finally { scene.close() }
    }

    @Test fun restrictedOrMissingProfilesShowTheSwitchCopyWithoutCredentialFields() {
        listOf(session(FakeStremioTransport(), restricted = true), null).forEach { value ->
            val scene = scene(390, 844, value, compact = true)
            try {
                val probe = Probe(scene); probe.frames(10)
                assertTrue(probe.has("Switch to an unrestricted profile to import."))
                assertNull(probe.fieldOrNull("Stremio password"))
            } finally { scene.close() }
        }
    }

    @Test fun phoneWidthWrapsLongNamesWithoutHorizontalOverflow() {
        val long = "An extraordinarily long profile name that keeps going " + "Unbroken".repeat(12)
        val transport = FakeStremioTransport()
        transport.preview = { stremioAddons(addons = listOf(StremioAddon(stremioAddonId, "Metadata add-on with a very long descriptive name " + "X".repeat(90), listOf("meta", "stream"), "add"))) }
        transport.review = { _, _ -> stremioReview(items = listOf(stremioMovie.copy(name = "A title whose name is far wider than any phone " + "W".repeat(80)), stremioEpisode)) }
        val scene = scene(390, 2400, session(transport, name = long), long, compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.assertInside(390, "connect")
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            probe.click("Next: Choose add-ons"); probe.assertInside(390, "add-ons")
            probe.capture("phone-addons")
            probe.click("Next: Review items"); probe.assertInside(390, "items")
            probe.capture("phone-items")
            probe.click("Next: Confirm import"); probe.assertInside(390, "confirm")
            probe.click("I confirm this import is for $long.")
            probe.click("Confirm import", button = true); probe.assertInside(390, "complete")
        } finally { scene.close() }
    }

    @Test fun pagedItemReviewSearchesAllRowsAndFiltersExcludedItems() {
        val counts = mapOf("favorites_to_add" to 1, "progress_to_add" to 0, "progress_to_update" to 0, "existing_preserved" to 0, "already_imported" to 0)
        val items = (0 until 120).map { stremioMovie.copy(id = "synthetic_review_${it.toString().padStart(8, '0')}", name = "Movie ${it.toString().padStart(3, '0')}", counts = counts) }
        val summary = mapOf("source_items" to 120, "favorites_to_add" to 120, "progress_to_add" to 0, "progress_to_update" to 0,
            "existing_preserved" to 0, "already_imported" to 0, "needs_review" to 0, "skipped_items" to 0)
        val transport = FakeStremioTransport()
        transport.review = { _, _ -> stremioReview(items = items, counts = summary) }
        val session = session(transport)
        val scene = scene(1000, 4000, session)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); probe.frames(10)
            assertNotNull(probe.described("Include Movie 000")); assertNull(probe.described("Include Movie 050"))
            probe.click("Next items")
            assertNotNull(probe.described("Include Movie 050"))
            probe.setText("Find a title", "Movie 11")
            assertNotNull(probe.described("Include Movie 119")); assertNull(probe.described("Include Movie 050"))
            probe.click("Exclude matching")
            assertEquals(10, session.state.value.excluded.size)
            probe.setText("Find a title", ""); probe.click("Excluded items")
            assertEquals(10, probe.nodes().count { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Include Movie") } == true })
            probe.click("Include matching")
            assertTrue(probe.has("No items match this filter."))
            assertTrue(session.state.value.excluded.isEmpty())
        } finally { scene.close() }
    }

    @Test fun importEntryIsGatedToBrowserAndAndroidRemoteSettings() {
        val profiles = listOf(Profile("2", "Synthetic profile", null))
        fun entries(width: Int, browser: Boolean, television: Boolean = false, desktop: Boolean = false, local: Boolean = false): List<SemanticsNode> {
            val controller = ApplicationController(scope, object : RemoteSessionStore {
                override suspend fun read(): SavedRemoteSession? = null
                override suspend fun save(session: SavedRemoteSession) = Unit
                override suspend fun clear() = Unit
            })
            controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2", profiles = profiles, localMode = local)
            val scene = ImageComposeScene(width, 900, Density(1f)) {
                ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SettingsRoute(controller.state.value, controller, browser, television, compact = width < 640, desktop = desktop)
                } }
            }
            try {
                val probe = Probe(scene); probe.frames(12)
                return probe.nodes().filter { probe.text(it).startsWith(stremioImportSection) && it.config.contains(SemanticsActions.OnClick) }
                    .also { if (it.isNotEmpty()) {
                        val nodes = probe.nodes()
                        val devices = nodes.firstOrNull { n -> probe.text(n) == "Devices" }
                        if (devices != null) assertTrue(it.single().boundsInRoot.top > devices.boundsInRoot.top, "Import follows Devices")
                    } }
            } finally { scene.close() }
        }
        assertEquals(1, entries(1440, browser = true).size, "Browser account sidebar")
        assertEquals(1, entries(390, browser = true).size, "Browser at phone width")
        assertEquals(1, entries(390, browser = false).size, "Android phone")
        assertEquals(1, entries(1000, browser = false).size, "Android tablet settings sidebar")
        assertTrue(entries(1440, browser = false, desktop = true).isEmpty(), "Desktop JVM")
        assertTrue(entries(390, browser = false, local = true).isEmpty(), "Local mode")
        assertTrue(entries(1920, browser = false, television = true).isEmpty(), "Television")
        val state = ApplicationState(selectedProfile = "2")
        assertTrue(stremioImportAvailable(state, television = false, desktop = false))
        assertFalse(stremioImportAvailable(state, television = true, desktop = false))
        assertFalse(stremioImportAvailable(state, television = false, desktop = true))
        assertFalse(stremioImportAvailable(state.copy(localMode = true), television = false, desktop = false))
    }

    @Test fun phoneSettingsOpensImportAndReturnsToTheList() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                stremioImport = { compact, back -> StremioImportScreen(null, "Synthetic profile", compact, back) }) }
        }
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.click(stremioImportSection)
            assertTrue(probe.has("Import into: Synthetic profile"))
            probe.click("Back to settings")
            assertTrue(probe.has("Settings")); assertFalse(probe.has("Import into: Synthetic profile"))
        } finally { scene.close() }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun has(value: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == value } == true }
        fun fieldOrNull(label: String) = nodes().firstOrNull { it.config.contains(SemanticsProperties.EditableText) && text(it).contains(label) }
        fun field(label: String) = requireNotNull(fieldOrNull(label)) { "Missing field $label" }
        fun editable(label: String) = field(label).config[SemanticsProperties.EditableText].text
        fun setText(label: String, value: String) { field(label).config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames(6) }
        fun described(value: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true }
        fun describedState(value: String) = requireNotNull(described(value)) { "Missing $value" }.config[SemanticsProperties.ToggleableState]
        fun clickDescription(value: String) { assertTrue(requireNotNull(described(value)).config[SemanticsActions.OnClick].action!!.invoke()); frames(10) }
        private fun clickable(label: String, button: Boolean) = nodes().filter { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true &&
                (!button || node.config.getOrNull(SemanticsProperties.Role) == Role.Button)
        }.let { matches -> matches.lastOrNull() ?: error("Missing action $label") }
        fun toggleState(label: String) = clickable(label, false).config[SemanticsProperties.ToggleableState]
        fun disabled(label: String, button: Boolean = false) = clickable(label, button).config.contains(SemanticsProperties.Disabled)
        fun click(label: String, button: Boolean = false) {
            val node = clickable(label, button)
            assertFalse(node.config.contains(SemanticsProperties.Disabled), "$label is disabled")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); frames(10)
        }
        fun focused(heading: String) = nodes().any { node -> node.config.contains(SemanticsProperties.Heading) &&
            text(node) == heading && node.config.getOrNull(SemanticsProperties.Focused) == true }
        fun assertInside(width: Int, stage: String) = nodes().forEach { node ->
            // Unclipped geometry: boundsInRoot would hide an overflowing child behind its parent's bounds.
            val left = node.positionInRoot.x; val right = left + node.size.width
            if (node.size.width > 0) assertTrue(left >= -.5f && right <= width + .5f,
                "$stage: '${text(node).take(40)}' overflows the phone width ($left..$right)")
        }
        fun capture(variant: String) {
            val directory = System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR") ?: return
            File(directory).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { File(directory, "stremio-import-$variant.png").writeBytes(it.bytes) }
            }
        }
    }
}
