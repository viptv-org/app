package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assume
import org.viptv.app.connection.*
import org.viptv.app.features.settings.StremioImportScreen
import org.viptv.app.features.settings.stremioDuplicateOrdinals
import org.viptv.app.state.*
import org.viptv.app.theme.*
import java.io.File
import kotlin.test.*

/**
 * Opt-in: `VIPTV_STREMIO_REAL_FIXTURES=<dir>` with privately captured `preview.json` and
 * `review-*.json` server responses. Skipped otherwise. The files stay outside the repository and
 * this test only ever prints or asserts aggregate counts and timings, never names or handles.
 */
@OptIn(ExperimentalComposeUiApi::class)
class StremioImportRealFixtureTest {
    @get:Rule val uiThread = UiThreadRule()
    private val directory = System.getenv("VIPTV_STREMIO_REAL_FIXTURES")?.takeIf { it.isNotBlank() }?.let(::File)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @AfterTest fun close() = scope.cancel()
    @BeforeTest fun requireFixtures() = Assume.assumeTrue("Set VIPTV_STREMIO_REAL_FIXTURES to a private capture directory",
        directory?.resolve("preview.json")?.isFile == true)

    private fun read(name: String) = directory!!.resolve(name).takeIf { it.isFile }?.readText()
    private fun record(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun profile(text: String) = record(text).getValue("profile_id").jsonPrimitive.content
    /** Captures expire after ten minutes; validate them as of one minute before their own expiry. */
    private fun capturedAt(text: String) = record(text).getValue("expires_at").jsonPrimitive.long * 1000 - 60_000
    private fun reviews() = listOf("review-none.json", "review-all.json").mapNotNull { name -> read(name)?.let { name to it } }

    @Test fun capturedResponsesPassTheRealClientValidation(): Unit = runBlocking {
        val preview = requireNotNull(read("preview.json"))
        val profile = profile(preview)
        var served = preview
        var now = capturedAt(preview)
        val client = HttpClient(MockEngine { respond(served, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }, client, browserMode = false)
        try {
            val api = RemoteStremioImport(remote, now = { now })
            val addons = assertIs<StremioAddonPreview>(api.preview(profile, "fixture@example.invalid", "unused", true, true),
                "The captured add-on stage must validate")
            val rawAddons = record(preview).getValue("addons").jsonArray.size
            assertEquals(rawAddons, addons.addons.size)
            println("real preview: ${addons.addons.size} add-ons, statuses ${addons.addons.groupingBy { it.status }.eachCount()}, " +
                "repeated names ${stremioDuplicateOrdinals(addons.addons).size}")
            val captured = reviews()
            assertTrue(captured.isNotEmpty(), "At least one review-*.json capture is expected")
            captured.forEach { (name, text) ->
                served = text; now = capturedAt(text)
                val review = api.review(profile, addons.previewId, emptyList(), 0)
                assertEquals(record(text).getValue("review_items").jsonArray.size, review.items.size, "$name rows all accepted")
                assertEquals(review.summary.getValue("favorites_to_add"), review.items.sumOf { it.counts.getValue("favorites_to_add") })
                println("real $name: ${review.items.size} rows, statuses ${review.items.groupingBy { it.status }.eachCount()}, " +
                    "selectable ${review.items.count { it.selectable }}, addons_to_add ${review.addonsToAdd}")
            }
        } finally { remote.close() }
    }

    @Test fun capturedReviewDrivesThePhoneUiResponsively() {
        val previewText = requireNotNull(read("preview.json"))
        val (name, reviewText) = reviews().first()
        val profile = profile(previewText)
        val future = System.currentTimeMillis() / 1000 + 600
        val addons = requireNotNull(parseAddons(record(previewText), profile, capturedAt(previewText))).copy(expiresAt = future)
        val review = requireNotNull(parseReview(record(reviewText), profile, capturedAt(reviewText))).copy(expiresAt = future, previewId = addons.previewId)
        val transport = FakeStremioTransport()
        transport.preview = { addons }
        transport.review = { _, _ -> review }
        val session = StremioImportSession(scope, transport, profile, "Fixture profile", false)
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { StremioImportScreen(session, "Fixture profile", compact = true) } }
        }
        var frame = 0L
        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun has(text: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
        fun click(label: String) {
            val node = nodes().last { it.config.contains(SemanticsActions.OnClick) && it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true }
            assertFalse(node.config.contains(SemanticsProperties.Disabled), "$label is disabled")
            node.config[SemanticsActions.OnClick].action!!.invoke(); frames(10)
        }
        fun search(value: String) {
            nodes().first { it.config.contains(SemanticsProperties.EditableText) }.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames(10)
        }
        fun scrollTo(index: Int) {
            nodes().first { it.config.contains(SemanticsActions.ScrollToIndex) }.config[SemanticsActions.ScrollToIndex].action!!.invoke(index); frames(10)
        }
        fun inside(stage: String) = nodes().forEach { node ->
            val left = node.positionInRoot.x; val right = left + node.size.width
            // Geometry only: messages never include captured names.
            if (node.size.width > 0) assertTrue(left >= -.5f && right <= 390.5f, "$stage: a node overflows the phone width")
        }
        try {
            frames(12)
            session.connect("fixture@example.invalid", "unused"); frames(10)
            assertEquals(StremioImportStage.Addons, session.state.value.stage)
            inside("real add-ons")
            session.continueWithoutAddons(); frames(10)
            assertEquals(StremioImportStage.Items, session.state.value.stage)
            val selectable = review.items.count { it.selectable }
            scrollTo(6) // item controls sit below the summary on a phone
            assertTrue(has("$selectable review items included · 0 excluded · ${review.items.size} shown in this preview"))
            inside("real review")
            val timings = linkedMapOf<String, Long>()
            fun timed(label: String, block: () -> Unit) { val start = System.nanoTime(); block(); timings[label] = (System.nanoTime() - start) / 1_000_000 }
            timed("type search") { search("e") }
            timed("exclude matching") { click("Exclude matching") }
            timed("clear search") { search("") }
            timed("exclude all") { click("Exclude all") }
            assertEquals(selectable, session.state.value.excluded.size)
            assertTrue(has("Include at least one item, or go back and choose a new add-on, to continue."))
            timed("include all") { click("Include all") }
            assertTrue(session.state.value.excluded.isEmpty())
            timed("needs review filter") { click("Needs review / unmatched") }
            timed("all items") { click("All items") }
            repeat(3) { nodes().first { it.config.contains(SemanticsActions.ScrollBy) }.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 100_000f); frames(6) }
            timed("next page") { click("Next items") }
            inside("real review page 2")
            session.toConfirm(); frames(10)
            assertEquals(StremioImportStage.Confirm, session.state.value.stage)
            scrollTo(5)
            assertTrue(has("Selected review items: $selectable. New account-wide add-ons: ${review.addonsToAdd}."))
            assertTrue(transport.calls.none { it.startsWith("apply") }, "Nothing is applied by this test")
            println("real $name phone timings (ms, 10 frames each): $timings")
            timings.forEach { (label, ms) -> assertTrue(ms < 4_000, "$label took $ms ms") }
        } finally { scene.close() }
    }
}
