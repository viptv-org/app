@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import org.viptv.app.components.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*
import kotlinx.coroutines.*

class TvLeadingFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Fixture(width: Int = 900, height: Int = 400, television: Boolean = true, content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: androidx.compose.ui.focus.FocusManager? = null
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) { focusManager = androidx.compose.ui.platform.LocalFocusManager.current; content() } }
        private var frame = 0L
        suspend fun settle(n: Int = 70) { repeat(n) { scene.render(frame++ * 16_666_667L).close(); delay(1) } }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun focused() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true } ?: error("No focused item")
        fun tag() = focused().config.getOrNull(SemanticsProperties.TestTag)
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            if (!consumed) focusManager!!.moveFocus(when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                else -> FocusDirection.Down
            })
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
        override fun close() = scene.close()
    }
    private fun near(expected: Float, actual: Float, message: String = "anchor") =
        assertTrue(kotlin.math.abs(expected - actual) <= 1f, "$message: expected $expected, got $actual")

    @Test fun billboardCoalescesHeldInputAndOnlyEnrichesTheSettledTitle() = ui {
        val cards = homeFixture().rows.first().cards
        var focused by mutableStateOf(cards[0])
        var quiet by mutableStateOf(0L)
        var selected: org.viptv.app.features.home.TvBillboardSelection? = null
        val enriched = mutableListOf<String>()
        Fixture {
            val result = org.viptv.app.features.home.rememberTvBillboardSelection(
                rememberUpdatedState(focused), { true }, { false }, { quiet },
                enrich = { enriched += it.identity.id })
            SideEffect { selected = result }
        }.use { f ->
            f.settle(2)
            focused = cards[1]; f.settle(2)
            focused = cards[2]; f.settle(2)
            assertNull(selected)
            quiet = 300L
            delay(260)
            f.settle(20)
            assertEquals(cards[2].identity, selected?.card?.identity)
            assertEquals(listOf(cards[2].identity.id), enriched)
        }
    }

    @Test fun portraitBlurCannotLeakBelowItsFadeMask() = ui {
        val file = java.io.File.createTempFile("tv-blur-boundary", ".png")
        val bitmap = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = bitmap.createGraphics()
        graphics.color = java.awt.Color.YELLOW; graphics.fillRect(0, 0, 64, 64); graphics.dispose()
        javax.imageio.ImageIO.write(bitmap, "png", file)
        val card = homeFixture().hero!!.copy(poster = file.toPath().toUri().toString())
        try {
            Fixture(1920, 1080) {
                org.viptv.app.features.home.TvHomeBackdrop(card, browsing = true, portrait = true)
            }.use { f ->
                f.settle(100)
                f.scene.render(3_000_000_000L).use { image ->
                    org.jetbrains.skia.Bitmap.makeFromImage(image).use { pixels ->
                        assertTrue(((pixels.getColor(100, 100) shr 16) and 255) > 40, "Fixture artwork must have loaded")
                        for (y in 620..700 step 10) {
                            assertTrue(((pixels.getColor(100, y) shr 16) and 255) < 20, "Blur leaks below fade mask at y=$y")
                        }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test fun artworkDissolveNeverExposesBackgroundAndKeepsLatestReadyImage() = ui {
        fun art(id: String, color: androidx.compose.ui.graphics.Color) = org.viptv.app.features.home.LoadedArtwork(id, androidx.compose.ui.graphics.painter.ColorPainter(color))
        var ready by mutableStateOf<org.viptv.app.features.home.LoadedArtwork?>(art("red", androidx.compose.ui.graphics.Color.Red))
        val scene = ImageComposeScene(32, 32, Density(1f)) {
            org.viptv.app.features.home.ArtworkDissolve(ready, Modifier.fillMaxSize())
        }
        var frame = 0L
        suspend fun pixel(): Int {
            delay(1)
            return scene.render(frame++ * 16_666_667L).use { image -> org.jetbrains.skia.Bitmap.makeFromImage(image).use { it.getColor(16, 16) } }
        }
        try {
            repeat(20) { pixel() }
            ready = art("green", androidx.compose.ui.graphics.Color.Green)
            repeat(4) { pixel() }
            ready = art("blue", androidx.compose.ui.graphics.Color.Blue)
            repeat(35) {
                val p = pixel()
                val sum = ((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255)
                assertTrue(sum in 253..257, "Dissolve darkened or flashed at frame $frame: $sum")
            }
            assertEquals(255, pixel() and 255)
            // Loading keeps the previous decoded bitmap.
            ready = null
            repeat(5) { assertEquals(255, pixel() and 255) }
        } finally { scene.close() }
    }

    @Test fun seasonSelectionDownEntersNewEpisodesWithoutDetour() = ui {
        val parent = org.viptv.engine.wire.Card(identity = org.viptv.engine.wire.MediaIdentity("show", "series", emptyList()), provider_id = "test", title = "Show", badges = emptyList())
        fun episode(season: Long) = parent.copy(title = "Episode $season", identity = org.viptv.engine.wire.MediaIdentity("e$season", "series", emptyList(), org.viptv.engine.wire.EpisodeCoordinates("aired", season, 1)))
        for ((loaded, cancel) in listOf(true to false, false to false, false to true)) {
            val elsewhere = FocusRequester()
            var episodes by mutableStateOf(if (loaded) listOf(episode(1), episode(2)) else listOf(episode(1)))
            val shelf = org.viptv.app.features.details.ShelfFocusHandle()
            Fixture(1920, 1080) {
                Column {
                Box(Modifier.size(20.dp).testTag("elsewhere").focusRequester(elsewhere).focusable())
                org.viptv.app.features.details.TvEpisodeShelf(parent, episodes, 1f, {}, {}, true, null, {},
                    seasonIndex = listOf(org.viptv.engine.wire.EpisodeSeason(1, 1, 0), org.viptv.engine.wire.EpisodeSeason(2, 1, 1)),
                    focus = shelf)
                }
            }.use { f ->
                f.settle(); assertEquals(org.viptv.app.features.details.ShelfFocus.Focused, shelf.request!!(org.viptv.app.features.details.ShelfLanding.Any)); f.settle()
                f.press(Key.DirectionUp); f.settle()
                f.press(Key.DirectionRight); f.settle()
                f.scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
                f.scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
                f.settle(2)
                f.press(Key.DirectionDown)
                if (!loaded) {
                    f.settle(10)
                    if (cancel) elsewhere.requestFocus()
                    f.settle(2)
                    episodes = listOf(episode(1), episode(2))
                }
                f.settle()
                val text = f.focused().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
                if (cancel) assertEquals("elsewhere", f.tag(), "A late page must not steal focus")
                else assertTrue(text.contains("Episode 2"), "loaded=$loaded focus=$text")
            }
        }
    }

    @Test fun visibleHorizontalCardsAnchorWithPaddingAndVariableWidths() = ui {
        val first = FocusRequester()
        Fixture { FocusLazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(24) { i -> Box(Modifier.width((100 + i % 3 * 20).dp).height(100.dp).testTag("$i")
                .then(if (i == 0) Modifier.focusRequester(first) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            repeat(4) { f.press(Key.DirectionRight); f.settle(); near(24f, f.focused().boundsInRoot.left) }
            assertEquals("4", f.tag())
            repeat(4) { f.press(Key.DirectionLeft); f.settle(); near(24f, f.focused().boundsInRoot.left) }
            assertEquals("0", f.tag())
        }
    }

    @Test fun rtlAndReverseLayoutsUseTheirActualLeadingEdge() = ui {
        for (rtl in listOf(false, true)) for (reverse in listOf(false, true)) {
            val target = FocusRequester()
            Fixture { CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FocusLazyRow(reverseLayout = reverse, contentPadding = PaddingValues(start = 24.dp, end = 40.dp)) {
                    items(24) { i -> Box(Modifier.size(100.dp).testTag("$i").then(if (i == 3) Modifier.focusRequester(target) else Modifier).focusable()) }
                }
            } }.use { f ->
                f.settle(); target.requestFocus(); f.settle()
                assertEquals("3", f.tag())
                val fromEnd = rtl xor reverse
                val inset = if (reverse) 40f else 24f
                if (fromEnd) near(900f - inset, f.focused().boundsInRoot.right, "rtl=$rtl reverse=$reverse")
                else near(inset, f.focused().boundsInRoot.left, "rtl=$rtl reverse=$reverse")
            }
        }
    }

    @Test fun verticalItemsAnchorAndStopAtThePhysicalEnd() = ui {
        val first = FocusRequester()
        Fixture { FocusLazyColumn(contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(12) { i -> Box(Modifier.width(200.dp).height(80.dp).testTag("$i").then(if (i == 0) Modifier.focusRequester(first) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            repeat(4) { f.press(Key.DirectionDown); f.settle(); near(32f, f.focused().boundsInRoot.top) }
            repeat(10) { f.press(Key.DirectionDown); f.settle(30) }
            f.settle(); assertEquals("11", f.tag()); near(376f, f.focused().boundsInRoot.bottom)
            assertTrue(f.focused().boundsInRoot.top > 32f)
        }
    }

    @Test fun gridKeepsColumnAndOnlyAnchorsTheRow() = ui {
        val first = FocusRequester()
        Fixture { FocusLazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(45) { i -> Box(Modifier.height(100.dp).tvGridItem(i).testTag("$i").then(if (i == 1) Modifier.focusRequester(first) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            val middle = f.focused().boundsInRoot.left
            repeat(3) { f.press(Key.DirectionDown); f.settle(); near(24f, f.focused().boundsInRoot.top); near(middle, f.focused().boundsInRoot.left) }
            assertEquals("10", f.tag())
            f.press(Key.DirectionRight); f.settle(); assertEquals("11", f.tag()); near(24f, f.focused().boundsInRoot.top)
            f.press(Key.DirectionLeft); f.settle(); near(middle, f.focused().boundsInRoot.left); near(24f, f.focused().boundsInRoot.top)
        }
    }

    @Test fun shortListsAndNonTvLayoutsKeepNativeLimits() = ui {
        for (tv in listOf(true, false)) {
            val target = FocusRequester()
            Fixture(television = tv) { FocusLazyRow(contentPadding = PaddingValues(24.dp)) {
                items(if (tv) 3 else 24) { i -> Box(Modifier.size(100.dp).testTag("$i").then(if (i == 2) Modifier.focusRequester(target) else Modifier).focusable()) }
            } }.use { f -> f.settle(); target.requestFocus(); f.settle(); near(224f, f.focused().boundsInRoot.left) }
        }
    }

    @Test fun rapidReversalDoesNotSkipFocusOrKeepAnOldScrollTarget() = ui {
        val first = FocusRequester()
        Fixture { FocusLazyRow(contentPadding = PaddingValues(24.dp)) {
            items(60) { i -> Box(Modifier.size(100.dp).testTag("$i").then(if (i == 0) Modifier.focusRequester(first) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            repeat(18) { f.press(Key.DirectionRight); f.settle(2); assertEquals("${it + 1}", f.tag()) }
            repeat(18) { f.press(Key.DirectionLeft); f.settle(2); assertEquals("${17 - it}", f.tag()) }
            f.settle(); near(24f, f.focused().boundsInRoot.left); assertEquals("0", f.tag())
        }
    }

    @Test fun focusedIdentityAndAnchorSurvivePrependingAndReordering() = ui {
        val target = FocusRequester()
        val data = mutableStateOf((0..23).toList())
        Fixture { FocusLazyRow(contentPadding = PaddingValues(24.dp)) {
            items(data.value, key = { it }) { i -> Box(Modifier.size(100.dp).testTag("$i").then(if (i == 3) Modifier.focusRequester(target) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); target.requestFocus(); f.settle(); near(24f, f.focused().boundsInRoot.left)
            data.value = listOf(99) + data.value; f.settle(); assertEquals("3", f.tag()); near(24f, f.focused().boundsInRoot.left)
            data.value = data.value.filter { it != 2 }; f.settle(); assertEquals("3", f.tag()); near(24f, f.focused().boundsInRoot.left)
        }
    }

    @Test fun ordinaryScrollableRowUsesTheSamePaddingAnchor() = ui {
        val target = FocusRequester()
        Fixture { TvFocusScroll(horizontal = true, contentPadding = PaddingValues(horizontal = 24.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                repeat(24) { i -> Box(Modifier.size(100.dp).testTag("$i").then(if (i == 3) Modifier.focusRequester(target) else Modifier).focusable()) }
            }
        } }.use { f -> f.settle(); target.requestFocus(); f.settle(); near(24f, f.focused().boundsInRoot.left) }
    }

    @Test fun nestedSectionsKeepHeadingWhileChildCardsAnchor() = ui {
        val first = FocusRequester()
        Fixture { val rows = rememberLazyListState(); val sections = rememberTvSectionScroll(rows)
            FocusLazyColumn(state = rows, sectionControlled = true, contentPadding = PaddingValues(top = 24.dp)) {
                items(10) { row -> Column(Modifier.height(180.dp)) {
                    Spacer(Modifier.height(40.dp))
                    FocusLazyRow(Modifier.onFocusChanged { if (it.hasFocus) sections.focus(row) }.focusGroup(), contentPadding = PaddingValues(horizontal = 24.dp)) {
                        items(20) { col -> Box(Modifier.size(100.dp).testTag("$row:$col").then(if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier).focusable()) }
                    }
                } }
            }
        }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            repeat(3) { f.press(Key.DirectionDown); f.settle(); near(64f, f.focused().boundsInRoot.top) }
            repeat(3) { f.press(Key.DirectionRight); f.settle(); near(64f, f.focused().boundsInRoot.top); near(24f, f.focused().boundsInRoot.left) }
            assertEquals("3:3", f.tag())
        }
    }
    @Test fun oversizedCardsSettleAtTheLeadingEdge() = ui {
        val target = FocusRequester()
        var list: androidx.compose.foundation.lazy.LazyListState? = null
        Fixture { val state = rememberLazyListState(); list = state
            FocusLazyRow(state = state, contentPadding = PaddingValues(24.dp)) {
                items(6) { i -> Box(Modifier.width(if (i == 1) 1100.dp else 100.dp).height(100.dp)
                    .testTag("$i").then(if (i == 1) Modifier.focusRequester(target) else Modifier).focusable()) }
            }
        }.use { f ->
            f.settle(); target.requestFocus(); f.settle()
            near(24f, f.focused().boundsInRoot.left); assertFalse(list!!.isScrollInProgress)
        }
    }

    private fun homeFixture(wide: Boolean = false): org.viptv.engine.wire.HomeSnapshot {
        val rows = (0..3).map { row -> org.viptv.engine.wire.HomeRow(
            id = if (wide && row == 0) "continue" else "row$row", title = "Heading $row", provider_id = "fixture",
            state = org.viptv.engine.wire.RowState.READY,
            cards = (0..23).map { col -> org.viptv.engine.wire.Card(
                org.viptv.engine.wire.MediaIdentity("$row:$col", "movie", emptyList()), provider_id = "fixture",
                title = "Card $row:$col", badges = emptyList()) }) }
        return org.viptv.engine.wire.HomeSnapshot(org.viptv.engine.wire.Scope("server", "owner", "profile", 1), 1,
            hero = rows[0].cards[0].copy(title = "Hero"), featured = emptyList(), rows = rows, complete = true)
    }
    private fun label(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun Fixture.focusCard(label: String) {
        val node = nodes().first { this@TvLeadingFocusTest.label(it) == label && it.config.contains(SemanticsActions.RequestFocus) }
        assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke())
    }

    @Test fun homeShelfFitsAtTheBottomWithoutNextHeadingAtBothTvSizes() = ui {
        for (width in listOf(1920, 1280)) for (wide in listOf(false, true)) {
            val scale = width / 1920f
            val home = homeFixture(wide)
            Fixture(width, width * 9 / 16) {
                org.viptv.app.features.home.TvHomeScreen(home, {}, {}, {}, {}, null, busy = false)
            }.use { f ->
                f.settle(); f.focusCard("Card 0:2"); f.settle()
                val bounds = f.focused().boundsInRoot
                near(96f * scale, bounds.left)
                near((if (wide) 422.4f else 255.2f) * scale, bounds.width)
                near((1080f - 24f) * scale, bounds.bottom, "complete shelf bottom")
                near((if (wide) 422.4f * 9f / 16f else 255.2f * 1.5f) * scale, bounds.height - 76f * scale, "art aspect ratio")
                assertTrue(bounds.top >= (516 + 24) * scale, "art must clear upper-right poster")
                assertTrue(f.nodes().filter { label(it) == "Heading 1" }.all { it.boundsInRoot.isEmpty || it.boundsInRoot.top >= 1080f * scale }, "next heading is visible")
                System.getenv("VIPTV_ANCHOR_CAPTURE_DIR")?.let { dir ->
                    java.io.File(dir).mkdirs()
                    f.scene.render().use { image -> java.io.File(dir, "home-$width-${if (wide) "wide" else "portrait"}.png")
                        .writeBytes(requireNotNull(image.encodeToData()).bytes) }
                }
            }
        }
    }

    @Test fun initialHeroKeepsTheFirstShelfPreviewAtItsOriginalPosition() = ui {
        for (wide in listOf(false, true)) {
            val home = homeFixture(wide)
            Fixture(1920, 1080) {
                org.viptv.app.features.home.TvHomeScreen(home, {}, {}, {}, {}, null, busy = false)
            }.use { f ->
                f.settle()
                val heading = f.nodes().first { label(it) == "Heading 0" }
                near(824f, heading.boundsInRoot.top, "initial hero shelf heading")
                f.focusCard("Card 0:2"); f.settle()
                f.press(Key.DirectionUp); f.settle()
                near(824f, f.nodes().first { label(it) == "Heading 0" }.boundsInRoot.top, "hero return heading")
            }
        }
    }

    @Test fun homeReturnsToTheSameAnchoredCardAfterRouteDisposal() = ui {
        val show = mutableStateOf(true)
        val home = homeFixture()
        Fixture(1920, 1080) {
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            if (show.value) saved.SaveableStateProvider("home") {
                org.viptv.app.features.home.TvHomeScreen(home, {}, {}, {}, {}, null, busy = false)
            }
        }.use { f ->
            f.settle(); f.focusCard("Card 0:2"); f.settle()
            f.press(Key.DirectionDown); f.settle()
            repeat(5) { f.press(Key.DirectionRight); f.settle() }
            val original = label(f.focused())
            show.value = false; f.settle(); show.value = true; f.settle(120)
            assertEquals(original, label(f.focused()))
            near(96f, f.focused().boundsInRoot.left)
        }
    }

    @Test fun catalogGridCardsMoveOneSlotAndKeepTheirColumn() = ui {
        val cards = homeFixture().rows.flatMap { it.cards }
        Fixture(1280, 720) {
            org.viptv.app.features.browse.TvBrowseScreen(cards, false, {})
        }.use { f ->
            f.settle(); f.focusCard("Card 0:0"); f.settle()
            val top = f.focused().boundsInRoot.top
            f.press(Key.DirectionRight); f.settle()
            assertEquals("Card 0:1", label(f.focused()))
            val column = f.focused().boundsInRoot.left
            f.press(Key.DirectionDown); f.settle()
            assertEquals("Card 0:8", label(f.focused()))
            near(column, f.focused().boundsInRoot.left); near(top, f.focused().boundsInRoot.top)
            f.press(Key.DirectionRight); f.settle()
            assertEquals("Card 0:9", label(f.focused())); near(top, f.focused().boundsInRoot.top)
            f.press(Key.DirectionUp); f.settle()
            assertEquals("Card 0:2", label(f.focused())); near(top, f.focused().boundsInRoot.top)
            for (cadence in listOf(3, 10, 20)) {
                repeat(6) { f.press(Key.DirectionDown); f.settle(cadence) }
                repeat(6) { f.press(Key.DirectionUp); f.settle(cadence) }
                f.settle(); assertEquals("Card 0:2", label(f.focused()), "cadence=$cadence")
            }
        }
    }

    @Test fun homeUpRestoresTheCardInAnUncomposedShelf() = ui {
        val home = homeFixture()
        Fixture(1280, 720) {
            org.viptv.app.features.home.TvHomeScreen(home, {}, {}, {}, {}, null, busy = false)
        }.use { f ->
            f.settle(); f.focusCard("Card 0:3"); f.settle()
            f.press(Key.DirectionDown); f.settle()
            repeat(2) { f.press(Key.DirectionRight); f.settle() }
            val remembered = label(f.focused())
            f.press(Key.DirectionDown); f.settle()
            repeat(3) { f.press(Key.DirectionRight); f.settle() }
            f.press(Key.DirectionUp); f.settle()
            assertEquals(remembered, label(f.focused()))
            f.press(Key.DirectionUp); f.settle()
            assertEquals("Card 0:3", label(f.focused()))
        }
    }

    @Test fun gridClampsPartialRowsAndPreservesTheColumnInRtl() = ui {
        val first = FocusRequester()
        Fixture { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FocusLazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(24.dp)) {
                items(14) { i -> Box(Modifier.height(100.dp).tvGridItem(i).testTag("$i")
                    .then(if (i == 2) Modifier.focusRequester(first) else Modifier).focusable()) }
            }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            val left = f.focused().boundsInRoot.left
            repeat(3) { f.press(Key.DirectionDown); f.settle(); near(left, f.focused().boundsInRoot.left) }
            f.press(Key.DirectionDown); f.settle(); assertEquals("13", f.tag())
            f.press(Key.DirectionUp); f.settle(); assertEquals("10", f.tag())
        }
    }

    @Test fun leavingGridCancelsAnOffscreenFocusRequest() = ui {
        val target = FocusRequester(); val outside = FocusRequester()
        Fixture { Column {
            Box(Modifier.size(50.dp).testTag("outside").focusRequester(outside).focusable())
            FocusLazyVerticalGrid(GridCells.Fixed(7), Modifier.weight(1f)) {
                items(70) { i -> Box(Modifier.height(160.dp).tvGridItem(i).testTag("$i")
                    .then(if (i == 9) Modifier.focusRequester(target) else Modifier).focusable()) }
            }
        } }.use { f ->
            f.settle(); target.requestFocus(); f.settle()
            f.press(Key.DirectionUp); outside.requestFocus(); f.settle()
            assertEquals("outside", f.tag())
        }
    }

    @Test fun appendingToAShortRowAnchorsTheAlreadyFocusedCardInBothDirections() = ui {
        for (rtl in listOf(false, true)) {
            val data = mutableStateOf((0..2).toList()); val target = FocusRequester()
            Fixture { CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FocusLazyRow(contentPadding = PaddingValues(24.dp)) {
                    items(data.value, key = { it }) { i -> Box(Modifier.size(100.dp).testTag("$i")
                        .then(if (i == 2) Modifier.focusRequester(target) else Modifier).focusable()) }
                }
            } }.use { f ->
                f.settle(); target.requestFocus(); f.settle(); assertEquals("2", f.tag())
                data.value = (0..23).toList(); f.settle()
                assertEquals("2", f.tag())
                if (rtl) near(876f, f.focused().boundsInRoot.right) else near(24f, f.focused().boundsInRoot.left)
            }
        }
    }

    @Test fun reorderingAClampedFocusedCardKeepsItVisibleAndAnchored() = ui {
        val data = mutableStateOf((0..9).toList()); val first = FocusRequester()
        Fixture { FocusLazyRow(contentPadding = PaddingValues(24.dp)) {
            items(data.value, key = { it }) { i -> Box(Modifier.size(100.dp).testTag("$i")
                .then(if (i == 0) Modifier.focusRequester(first) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); first.requestFocus(); f.settle()
            repeat(9) { f.press(Key.DirectionRight); f.settle(15) }; f.settle()
            assertEquals("9", f.tag())
            data.value = listOf(0, 9) + (1..8); f.settle()
            assertEquals("9", f.tag()); near(24f, f.focused().boundsInRoot.left)
        }
    }

    @Test fun aGridReanchorsItsFocusedRowWhenAnotherPageArrives() = ui {
        val count = mutableIntStateOf(9); val target = FocusRequester()
        Fixture { FocusLazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(24.dp)) {
            items(count.intValue, key = { it }) { i -> Box(Modifier.height(100.dp).tvGridItem(i).testTag("$i")
                .then(if (i == 8) Modifier.focusRequester(target) else Modifier).focusable()) }
        } }.use { f ->
            f.settle(); target.requestFocus(); f.settle()
            val column = f.focused().boundsInRoot.left
            count.intValue = 45; f.settle()
            assertEquals("8", f.tag()); near(24f, f.focused().boundsInRoot.top); near(column, f.focused().boundsInRoot.left)
        }
    }

}
