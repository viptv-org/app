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
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.*
import org.viptv.app.theme.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class StremioImportUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @AfterTest fun close() = scope.cancel()
    private fun session(transport: StremioImportTransport, restricted: Boolean = false, name: String = "Synthetic profile") =
        StremioImportSession(scope, transport, "2", name, restricted)
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
            assertTrue(probe.has("Review add-ons, verify metadata, then choose exactly which items to import. Viewing data goes to Synthetic profile. Selected add-ons are shared across your account's profiles."))
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
        val session = session(transport)
        val scene = scene(1000, 1200, session)
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

    @Test fun phoneAddonStageOffersEveryChoiceAndBringsTheFirstFailedAddonIntoView() {
        val addons = assertNotNull(parseAddons(Json.parseToJsonElement(StremioSyntheticFixtures.addonsJson("2")), "2", System.currentTimeMillis()))
            .copy(previewId = stremioHandle)
        val failing = listOf(StremioSyntheticFixtures.addonIds[17], StremioSyntheticFixtures.addonIds[19])
        val transport = FakeStremioTransport()
        transport.preview = { addons }
        transport.review = { chosen, _ ->
            chosen.filter { it in failing }.takeIf { it.isNotEmpty() }?.let { throw StremioImportFailure(424, "stremio_addon_unavailable", it,
                mapOf(failing[0] to "addon_unreachable", failing[1] to "a_future_category")) }
            stremioReview(addonsToAdd = chosen.size)
        }
        val session = session(transport)
        val scene = scene(390, 844, session, compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            probe.scrollToEnd(); probe.click("Next: Choose add-ons")
            assertTrue(probe.has("Step 2 of 4: Choose add-ons"))
            assertTrue(probe.has("Do you want to bring these add-ons into VIPTV? Uncheck any you do not want."))
            assertTrue(probe.has("21 of 21 available add-ons selected · 21 new to your account"))
            assertEquals(ToggleableState.On, probe.describedState("Use add-on Cinemeta"))
            assertNotNull(probe.described("Use add-on Synthetic Torrent (1 of 2)"), "Repeated names are numbered in Stremio order")
            listOf("Select all", "Clear all", "Continue without add-ons", "Next: Review items").forEach { assertNotNull(probe.clickableOrNull(it), it) }
            probe.assertInside(390, "phone add-ons")
            probe.capture("phone-addons-choice")
            probe.click("Next: Review items")
            assertTrue(probe.has("An add-on could not be verified. Change your add-on choices and try again."))
            assertTrue(probe.has("Couldn't check: Synthetic Debrid, Ñandú Meta."))
            probe.frames(60)
            val first = assertNotNull(probe.described("Use add-on Synthetic Debrid"), "The first failed add-on is scrolled into view")
            assertEquals(true, first.config.getOrNull(SemanticsProperties.Focused), "and focused")
            assertTrue(probe.has("This add-on didn't respond. Try again or uncheck it."))
            assertTrue(probe.has("Couldn't be checked"))
            probe.capture("phone-addons-failed")
            probe.scrollList(0)
            assertTrue(probe.has("2 add-ons couldn't be checked. Uncheck them, or continue without add-ons."))
            probe.scrollList(26)
            assertTrue(probe.has("This add-on couldn't be checked. Uncheck it or try again."), "Unknown categories get generic copy")
            probe.clickDescription("Use add-on Synthetic Debrid")
            assertEquals(setOf(failing[1]), session.state.value.failedAddons, "Unticking clears that add-on's mark")
            probe.click("Uncheck failed add-ons")
            assertTrue(session.state.value.failedAddons.isEmpty())
            assertEquals(ToggleableState.Off, probe.describedState("Use add-on Ñandú Meta"))
            assertNull(probe.clickableOrNull("Uncheck failed add-ons"))
            probe.click("Next: Review items")
            assertTrue(probe.has("Review items"))
            assertEquals(19, transport.calls.last().substringAfter("[").substringBefore("]").split(", ").size)
        } finally { scene.close() }
    }

    @Test fun cancellingALongVerificationOnAPhoneKeepsTheChoices(): Unit = runBlocking {
        val transport = FakeStremioTransport()
        val gate = CompletableDeferred<StremioReview>()
        transport.review = { _, _ -> gate.await() }
        val session = session(transport)
        val scene = scene(390, 844, session, compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); probe.frames(6)
            probe.click("Next: Review items")
            assertTrue(probe.has("Verifying metadata and preparing named items…"))
            assertTrue(probe.has("This can take up to a minute for large libraries or many add-ons. Nothing is saved yet."))
            assertTrue(probe.disabled("Next: Review items"))
            probe.click("Cancel")
            assertTrue(probe.has(stremioReviewStoppedCopy))
            assertFalse(probe.has("Verifying metadata and preparing named items…"))
            assertEquals(ToggleableState.On, probe.describedState("Use add-on Synthetic metadata"))
            gate.complete(stremioReview()); probe.frames(6)
            assertTrue(probe.has("Step 2 of 4: Choose add-ons"))
        } finally { scene.close() }
    }

    @Test fun twoThousandRowReviewStaysResponsiveAtPhoneWidth() {
        val review = assertNotNull(parseReview(Json.parseToJsonElement(StremioSyntheticFixtures.reviewJson(2_000, profile = "2")), "2", System.currentTimeMillis()))
            .copy(previewId = stremioHandle)
        val transport = FakeStremioTransport()
        transport.review = { _, _ -> review }
        val session = session(transport)
        val scene = scene(390, 844, session, compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); probe.frames(10)
            probe.scrollList(6) // The item controls, below the summary on a phone
            assertTrue(probe.has("1920 review items included · 0 excluded · 2000 shown in this preview"))
            val timings = linkedMapOf<String, Long>()
            fun timed(label: String, block: () -> Unit) { val start = System.nanoTime(); block(); timings[label] = (System.nanoTime() - start) / 1_000_000 }
            val saga = review.items.filter { "saga" in it.name.lowercase() }
            timed("search") { probe.setText("Find a title", "saga") }
            assertTrue(probe.has("Showing 1–${minOf(50, saga.size)} of ${saga.size} matching"))
            timed("exclude matching") { probe.click("Exclude matching") }
            assertEquals(saga.count { it.selectable }, session.state.value.excluded.size)
            timed("clear search") { probe.setText("Find a title", "") }
            timed("exclude all") { probe.click("Exclude all") }
            assertTrue(probe.has("0 review items included · 1920 excluded · 2000 shown in this preview"))
            assertTrue(probe.has("Include at least one item, or go back and choose a new add-on, to continue."))
            assertTrue(probe.disabled("Next: Confirm import"))
            timed("include all") { probe.click("Include all") }
            timed("filter") { probe.click("Needs review / unmatched") }
            assertTrue(probe.has("Showing 1–50 of 80 matching"))
            probe.scrollToEnd()
            assertTrue(probe.has("Page 1 of 2 · 80 matching"))
            timed("next page") { probe.click("Next items") }
            assertNotNull(probe.nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Include ") } == true },
                "The next page's first rows are scrolled into view")
            probe.assertInside(390, "large review")
            probe.capture("phone-large-review")
            println("2000-row phone review timings (ms, includes 10 frames each): $timings")
            timings.forEach { (label, ms) -> assertTrue(ms < 4_000, "$label took $ms ms") }
        } finally { scene.close() }
    }

    @Test fun tabletWidthUsesTwoSummaryColumnsWithinAReadableWidth() {
        val transport = FakeStremioTransport()
        val session = session(transport)
        val scene = scene(1280, 1200, session)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); probe.frames(10)
            val left = probe.textNode("Source library records").positionInRoot.x
            val right = probe.textNode("Existing entries preserved").positionInRoot.x
            assertTrue(right - left > 300, "Summary uses two columns ($left, $right)")
            probe.assertTextInside(760, "tablet content width")
            probe.capture("tablet-items")
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
            assertTrue(probe.has("Showing 1–50 of 120 matching"))
            probe.scrollToEnd(); probe.click("Next items")
            assertNotNull(probe.described("Include Movie 050"))
            probe.scrollList(0)
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

    /** Dashboard parity: every row detail, add-on status, the connect draft, the recap and the locked retry. */
    @Test fun rowsDraftRecapRetryAndCompletionShowEveryExplanationAndDetail() {
        val none = mapOf("favorites_to_add" to 0, "progress_to_add" to 0, "progress_to_update" to 0, "existing_preserved" to 0, "already_imported" to 0)
        val items = listOf(
            stremioMovie.copy(id = "synthetic_row_resume_12345", name = "Resumed movie", favoriteAction = "none", progressAction = "update",
                counts = none + ("progress_to_update" to 1), position = 754.0, duration = 5400.0),
            stremioEpisode.copy(id = "synthetic_row_watched_1234", name = "Watched series", season = 2, episode = 3, favoriteAction = "none",
                progressAction = "preserve", status = "preserved", counts = none + ("existing_preserved" to 1), watched = true,
                watchDateKnown = false, completionOnly = true, resumeActive = true),
            stremioMovie.copy(id = "synthetic_row_review_12345", name = "Unmatched title", favoriteAction = "none", progressAction = "none",
                status = "needs_review", selectable = false, counts = none, reason = "watch_date_unknown"))
        val counts = mapOf("source_items" to 3, "favorites_to_add" to 0, "progress_to_add" to 0, "progress_to_update" to 1,
            "existing_preserved" to 1, "already_imported" to 0, "needs_review" to 1, "skipped_items" to 0)
        val transport = FakeStremioTransport()
        transport.preview = { stremioAddons(addons = listOf(
            StremioAddon("existing_addon_12345678", "Existing metadata", listOf("meta"), "existing"),
            StremioAddon(stremioAddonId, "New metadata", listOf("meta", "stream"), "add"),
            StremioAddon("broken_addon_12345678901", "Broken", listOf("stream"), "unavailable", "unsupported_transport"))) }
        transport.review = { _, _ -> stremioReview(items = items, counts = counts, addonsToAdd = 1) }
        transport.apply = { throw StremioTransportFailure(true) }
        var managed = 0
        val session = session(transport)
        val scene = ImageComposeScene(1000, 3000, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                StremioImportScreen(session, "Synthetic profile", false, null) { managed++ }
            } }
        }
        try {
            val probe = Probe(scene); probe.frames(12)
            probe.click("Manage existing add-ons"); assertEquals(1, managed)
            assertTrue(probe.has("Your password is used for this request only and is not saved by VIPTV."))
            probe.setText("Stremio email", "synthetic@example.invalid"); probe.setText("Stremio password", "synthetic-password")
            probe.click("Next: Choose add-ons")
            assertTrue(probe.has("Already configured in your account")); assertTrue(probe.has("Add to your account on confirmation"))
            assertTrue(probe.has("Unavailable — not added")); assertTrue(probe.has("This add-on uses an unsupported connection method."))
            assertTrue(probe.has("Selected new add-ons are account-wide, not limited to this profile. Their private configuration is encrypted and is never shown here. Nothing is added until final confirmation."))
            assertTrue(probe.has("2 of 2 available add-ons selected · 1 new to your account"))
            probe.click("Back")
            assertTrue(probe.focused("Connect Stremio"))
            assertTrue(probe.has("Stremio is connected for this preview. Your add-on and item choices are still saved in this draft."))
            assertTrue(probe.has("synthetic@example.invalid")); assertTrue(probe.has("My List · Watch history and resume"))
            assertTrue(probe.has("Your password was cleared after connecting. Changing the connection starts a new preview."))
            assertNull(probe.fieldOrNull("Stremio password")); assertNotNull(probe.clickableOrNull("Change connection"))
            probe.click("Next: Choose add-ons"); probe.click("Next: Review items")
            assertTrue(probe.has("New add-ons to register for your account: 1"))
            assertTrue(probe.has("Resume 12:34 of 1:30:00")); assertTrue(probe.has("History/resume: Update older entry"))
            assertTrue(probe.has("Series · Season 2, episode 3")); assertTrue(probe.has("History/resume: Keep existing entry"))
            assertTrue(probe.has("Watched · Rewatch in progress · watch date unavailable"))
            assertTrue(probe.has("Completion only — does not change Continue Watching"))
            assertTrue(probe.has("Preserving existing data")); assertTrue(probe.has("Needs review — left unchanged"))
            assertTrue(probe.has("Watched state is known, but the episode watch date is unavailable. Left unchanged."))
            assertEquals(ToggleableState.Off, probe.describedState("Include Unmatched title"))
            assertTrue(probe.described("Include Unmatched title")!!.config.contains(SemanticsProperties.Disabled), "Needs-review rows cannot be included")
            probe.clickDescription("Include Resumed movie")
            assertTrue(probe.has("Excluded — will not be imported"))
            assertTrue(probe.has("1 review items included · 1 excluded · 3 shown in this preview"))
            probe.click("Next: Confirm import")
            assertTrue(probe.has("Review your chosen import into Synthetic profile."))
            assertTrue(probe.has("Selected review items: 1. New account-wide add-ons: 1."))
            assertTrue(probe.has("• Existing metadata (already configured)")); assertTrue(probe.has("• New metadata"))
            assertTrue(probe.has("Selected new add-ons are added to your account only when you confirm."))
            probe.click("I confirm this import is for Synthetic profile.")
            probe.click("Confirm import", button = true)
            assertTrue(probe.has(stremioUncertainApplyCopy))
            assertTrue(probe.has("This selection is locked while its result is being confirmed. Retrying uses the same items."))
            assertTrue(probe.disabled("Back")); assertTrue(probe.disabled("Cancel"))
            transport.apply = { stremioCompletion.copy(addonsAdded = 1, excludedItems = 1) }
            probe.click("Confirm import", button = true)
            val applies = transport.calls.filter { it.startsWith("apply") }
            assertEquals(2, applies.size); assertEquals(applies[0], applies[1], "Retry sends the identical selection")
            assertTrue(probe.focused("Import complete"))
            assertTrue(probe.has("Add-ons added to your account: 1")); assertTrue(probe.has("Review items excluded: 1"))
            assertTrue(probe.has("Start another preview"))
        } finally { scene.close() }
    }

    /** The server keeps filling imported titles' Details cards after apply; the completion stage shows it without blocking. */
    @Test fun completionShowsAnAnimatedTitleDetailsBarAndKeepsEveryActionAvailable() {
        var status = StremioDetailsStatus(true, 0, 0)
        val transport = FakeStremioTransport()
        transport.details = { status }
        val session = StremioImportSession(scope, transport, "2", "Synthetic profile", false, detailsPoll = 5)
        var left = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                StremioImportScreen(session, "Synthetic profile", true, back = { left++ })
            } }
        }
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); session.toConfirm()
            session.setConfirmed(true); session.apply(); probe.frames(12)
            assertTrue(probe.has("Import complete")); assertTrue(probe.focused("Import complete"))
            val label = probe.textNode("Getting title details…")
            assertEquals(LiveRegionMode.Polite, label.config.getOrNull(SemanticsProperties.LiveRegion), "Announced like the busy status")
            assertEquals(ProgressBarRangeInfo.Indeterminate, probe.progress().config[SemanticsProperties.ProgressBarRangeInfo],
                "Totals are not known yet")
            val before = probe.pixels(probe.progress()); probe.frames(18)
            assertNotEquals(before, probe.pixels(probe.progress()), "The indeterminate bar moves")
            assertTrue(probe.has("You can leave this screen. Details keep filling in the background."))
            assertFalse(probe.disabled("Start another preview")); assertFalse(probe.disabled("Back to settings"))
            probe.capture("phone-details-starting")

            status = StremioDetailsStatus(true, 40, 162); probe.frames(12)
            assertTrue(probe.has("40 of 162 titles"))
            val moving = probe.progress().config[SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue(moving > 0f && moving < 40f / 162, "The bar animates toward the new count ($moving)")
            probe.frames(150)
            assertEquals(40f / 162, probe.progress().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
            assertTrue(probe.focused("Import complete"), "Progress updates never move focus")
            probe.capture("phone-details-progress")
            status = StremioDetailsStatus(true, 120, 162); probe.frames(60)
            probe.capture("phone-details-progress-later")

            status = StremioDetailsStatus(false, 162, 162, 162); probe.frames(12)
            assertTrue(probe.has("Title details are ready."))
            assertTrue(probe.has("Imported titles now show their description, artwork and ratings."))
            assertEquals(LiveRegionMode.Polite, probe.textNode("Title details are ready.").config.getOrNull(SemanticsProperties.LiveRegion))
            assertNull(probe.progressOrNull(), "The bar goes once the fill is finished")
            probe.assertInside(390, "details ready")
            probe.capture("phone-details-ready")
            probe.click("Back to settings"); assertEquals(1, left)
        } finally { scene.close() }
    }

    @Test fun aFinishedFillSaysHowManyTitlesActuallyGotDetails() {
        val transport = FakeStremioTransport()
        transport.details = { StremioDetailsStatus(false, 163, 163, 0) }
        val session = StremioImportSession(scope, transport, "2", "Synthetic profile", false, detailsPoll = 5)
        val scene = scene(390, 844, session, compact = true)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); session.toConfirm()
            session.setConfirmed(true); session.apply()
            repeat(20) { if (!probe.has("Finished getting title details.")) probe.frames(3) }
            assertTrue(probe.has("Finished getting title details."), "Not \"ready\" when no card was filled")
            assertTrue(probe.has("Details were found for 0 of 163 titles. The others keep their name and poster."))
            assertNull(probe.progressOrNull())
            probe.assertInside(390, "partly filled")
            probe.capture("phone-details-partly")
        } finally { scene.close() }
    }

    @Test fun aFillLeftToTheBackgroundSaysSoWithoutABar() {
        val transport = FakeStremioTransport()
        transport.details = { throw StremioTransportFailure(false) }
        val session = StremioImportSession(scope, transport, "2", "Synthetic profile", false, detailsPoll = 5)
        val scene = scene(1000, 1200, session)
        try {
            val probe = Probe(scene); probe.frames(12)
            session.connect("synthetic@example.invalid", "synthetic-password"); session.reviewItems(); session.toConfirm()
            session.setConfirmed(true); session.apply()
            repeat(20) { if (!probe.has("Title details are still being added in the background.")) probe.frames(3) }
            assertTrue(probe.has("Title details are still being added in the background."))
            assertNull(probe.progressOrNull())
            assertTrue(probe.has("Start another preview"))
        } finally { scene.close() }
    }

    /** TV and desktop have no import entry; the `ImportDetails` preview renders the same completion stage there for review. */
    @Test fun importDetailsPreviewReachesTheCompletionStageOnTelevision() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            org.viptv.app.preview.UiPreview(initialScreen = Screen.Settings, television = true, tvDetailVariant = "ImportDetails")
        }
        try {
            val probe = Probe(scene)
            repeat(60) { if (!probe.has("Getting title details…")) probe.frames(3) }
            assertTrue(probe.has("Import complete")); assertTrue(probe.has("Getting title details…"))
            assertNotNull(probe.progressOrNull())
            assertTrue(probe.has("Start another preview"))
            probe.capture("tv-preview-details")
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
                stremioImport = { compact, back, _ -> StremioImportScreen(null, "Synthetic profile", compact, back) }) }
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
        }.lastOrNull()
        fun clickableOrNull(label: String) = clickable(label, false)
        private fun clickableOrFail(label: String, button: Boolean) = clickable(label, button) ?: error("Missing action $label")
        fun textNode(value: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == value } == true }
        /** Scrolls the import's lazy list, as a user would, so off-screen entries compose. */
        fun scrollToEnd() {
            repeat(3) { nodes().first { it.config.contains(SemanticsActions.ScrollBy) }.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 100_000f); frames(6) }
        }
        fun scrollList(index: Int) {
            val list = nodes().first { it.config.contains(SemanticsActions.ScrollToIndex) }
            list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index); frames(10)
        }
        fun toggleState(label: String) = clickableOrFail(label, false).config[SemanticsProperties.ToggleableState]
        fun disabled(label: String, button: Boolean = false) = clickableOrFail(label, button).config.contains(SemanticsProperties.Disabled)
        fun click(label: String, button: Boolean = false) {
            val node = clickableOrFail(label, button)
            assertFalse(node.config.contains(SemanticsProperties.Disabled), "$label is disabled")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); frames(10)
        }
        fun progressOrNull() = nodes().firstOrNull { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) }
        fun progress() = requireNotNull(progressOrNull()) { "Missing progress bar" }
        /** The rendered colours across the middle of [node], to tell whether it is drawn differently between frames. */
        fun pixels(node: SemanticsNode): List<Int> = scene.render(frame++ * 16_666_667L).use { image ->
            val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
            val bounds = node.boundsInRoot
            val y = ((bounds.top + bounds.bottom) / 2).toInt()
            (bounds.left.toInt() until bounds.right.toInt()).map { bitmap.getColor(it, y) }
        }
        fun focused(heading: String) = nodes().any { node -> node.config.contains(SemanticsProperties.Heading) &&
            text(node) == heading && node.config.getOrNull(SemanticsProperties.Focused) == true }
        fun assertInside(width: Int, stage: String) = nodes().forEach { node ->
            // Unclipped geometry: boundsInRoot would hide an overflowing child behind its parent's bounds.
            val left = node.positionInRoot.x; val right = left + node.size.width
            if (node.size.width > 0) assertTrue(left >= -.5f && right <= width + .5f,
                "$stage: '${text(node).take(40)}' overflows the phone width ($left..$right)")
        }
        fun assertTextInside(width: Int, stage: String) = nodes().filter { it.config.contains(SemanticsProperties.Text) }.forEach { node ->
            val right = node.positionInRoot.x + node.size.width
            assertTrue(right <= width + .5f, "$stage: text overflows $width ($right)")
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
