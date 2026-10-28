package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.connection.*
import org.viptv.app.features.home.DesktopHero
import org.viptv.app.features.home.HERO_ARTWORK_TAG
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * app#94: the desktop billboard is stable for the life of the screen. Hovering a
 * hero button changes that button and nothing else, and a quiet Home rebuild
 * that comes back without artwork (the server's metadata pass has a deadline)
 * keeps the backdrop and logo the viewer is already looking at instead of
 * repainting the billboard as a flat block.
 *
 * The report's "first hover" caught the rebuild landing; the hover itself is
 * proven clean here, and the rebuild is replayed through the real controller.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopHeroHoverTest {
    @get:Rule val uiThread = UiThreadRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    // No avatar, so the fake bootstrap rebuild leaves the title bar bit-identical.
    private val profile = Profile("12", "Parent", null)
    private val billboard = PreviewData.hero
    private val shown = PreviewData.home.copy(hero = billboard, featured = listOf(billboard))

    /** The same title after a rebuild whose metadata pass missed: no landscape, poster or logo. */
    private fun sparse(card: Card) = card.copy(landscape = null, poster = null, logo = null)
    private fun rebuilt() = shown.copy(revision = 8, hero = sparse(billboard), featured = listOf(sparse(billboard)))

    private val revisions = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0",
        "preferences" to "1", "profiles" to "7")

    private fun bootstrap(home: HomeSnapshot, revisions: Map<String, String>) = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(home)); put("integrations", JsonArray(emptyList()))
        put("revisions", JsonObject(revisions.mapValues { (_, value) -> JsonPrimitive(value) }))
    }.toString()

    private class Shell(val scene: ImageComposeScene) {
        private var frame = 0L
        /** Renders past the state layers' short animations, so two looks at the same state are equal. */
        fun pixels(): Bitmap {
            repeat(40) { scene.render(frame++ * 16_666_667L).close() }
            return scene.render(frame++ * 16_666_667L).use { Bitmap.makeFromImage(it) }
        }
        fun nodes(merged: Boolean = true) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }
        fun tagged(tag: String) = nodes(merged = false).filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun labelled(text: String) = nodes(merged = false).filter { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(text)
        }
        fun button(label: String) = nodes().filter { node -> node.config.contains(SemanticsActions.OnClick) &&
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
            .also { assertTrue(it.isNotEmpty(), "the $label button is on screen") }.first()
    }

    private fun shell(test: suspend (Shell, ApplicationController) -> Unit) = runBlocking {
        // Revisions moved elsewhere, so the foreground check rebuilds Home.
        val moved = revisions + ("library" to "5")
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/profiles/12/revisions" -> JsonObject(moved.mapValues { (_, v) -> JsonPrimitive(v) }).toString()
                "/api/bootstrap" -> bootstrap(rebuilt(), moved)
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, profiles = listOf(profile),
            selectedProfile = profile.id, home = shown)
        controller.freshness.homeBuilt(revisions)
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            DesktopShell(controller, GlobalBack(controller), mac = false,
                DesktopWindowControls(minimize = {}, maximize = {}, close = {}), manageControllerLifecycle = false)
        }
        try { Shell(scene).apply { pixels() }.let { shell -> test(shell, controller) } }
        finally { scene.close(); controller.disconnect(); scope.cancel(); remote.close() }
    }

    /** Runs the quiet lane to its end; the test asserts what it published. */
    private suspend fun settle(controller: ApplicationController) {
        while (true) { val running = controller.quietJob ?: return; running.join(); if (controller.quietJob === running) return }
    }

    /** Pixels that differ outside [except], allowing the control's own anti-aliased halo. */
    private fun changedPixels(before: Bitmap, after: Bitmap, except: Rect? = null): Int {
        var changed = 0
        for (x in 0 until before.width) for (y in 0 until before.height) {
            if (except != null && x >= except.left - 2 && x <= except.right + 2 && y >= except.top - 2 && y <= except.bottom + 2) continue
            if (before.getColor(x, y) != after.getColor(x, y)) changed++
        }
        return changed
    }

    /** NAV-10: a blank artwork URL is no artwork, so it gets the branded billboard and never a flat fill. */
    @Test fun aBlankArtworkUrlPaintsTheBrandedBillboard() = runBlocking {
        fun capture(card: Card): Shell {
            val scene = ImageComposeScene(1200, 700, Density(1f)) {
                ViptvTheme(pointer = true) { DesktopHero(card, {}, {}) }
            }
            return Shell(scene)
        }
        val blank = capture(billboard.copy(landscape = "", poster = ""))
        val absent = capture(billboard.copy(landscape = null, poster = null))
        try {
            blank.pixels(); absent.pixels()
            assertTrue(blank.tagged(HERO_ARTWORK_TAG).isEmpty(), "a blank URL never becomes the artwork layer")
            val withBlank = blank.pixels(); val withoutArt = absent.pixels()
            assertEquals(0, changedPixels(withBlank, withoutArt), "a blank URL paints the same billboard as no artwork at all")
        } finally { blank.scene.close(); absent.scene.close() }
    }

    @Test fun hoveringTheHeroPlayButtonRepaintsOnlyTheButton() = shell { shell, _ ->
        val rest = Offset(1420f, 860f)
        shell.scene.sendPointerEvent(PointerEventType.Move, rest)
        val artwork = shell.tagged(HERO_ARTWORK_TAG).single().boundsInRoot
        val resting = shell.pixels()
        val play = shell.button("Play").boundsInRoot

        shell.scene.sendPointerEvent(PointerEventType.Move, play.center)
        val hovered = shell.pixels()
        assertTrue(changedPixels(resting, hovered) > 0, "hovering Play changes how it looks")
        assertEquals(0, changedPixels(resting, hovered, play), "hovering Play changes nothing but the button")
        assertEquals(artwork, shell.tagged(HERO_ARTWORK_TAG).single().boundsInRoot, "hovering Play does not move the hero artwork")
        assertEquals(play, shell.button("Play").boundsInRoot, "hovering Play does not move the button")

        shell.scene.sendPointerEvent(PointerEventType.Move, rest)
        assertEquals(0, changedPixels(resting, shell.pixels()), "leaving Play restores the billboard and the button")
    }

    @Test fun aQuietRebuildKeepsTheBillboardArtworkOnScreen() = shell { shell, controller ->
        shell.scene.sendPointerEvent(PointerEventType.Move, Offset(1420f, 860f))
        val artwork = shell.tagged(HERO_ARTWORK_TAG).single().boundsInRoot
        val logo = shell.labelled(billboard.title).also {
            assertTrue(it.isNotEmpty(), "the billboard shows its logo")
        }.first().boundsInRoot
        val play = shell.button("Play").boundsInRoot
        val before = shell.pixels()

        controller.freshen(); settle(controller)
        assertEquals(8L, controller.state.value.home?.revision, "the rebuild landed")
        assertEquals(artwork, shell.tagged(HERO_ARTWORK_TAG).single().boundsInRoot, "the rebuild keeps the billboard artwork in place")
        assertEquals(logo, shell.labelled(billboard.title).first().boundsInRoot, "the rebuild keeps the billboard logo in place")
        assertEquals(play, shell.button("Play").boundsInRoot, "the rebuild keeps the actions where they were")
        assertEquals(0, changedPixels(before, shell.pixels()), "the rebuild paints the same billboard")
    }
}
