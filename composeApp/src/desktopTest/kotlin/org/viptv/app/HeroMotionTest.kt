package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.features.home.DesktopHero
import org.viptv.app.features.home.HERO_ARTWORK_TAG
import org.viptv.app.features.home.HERO_SUMMARY_TAG
import org.viptv.app.features.home.HeroCarousel
import org.viptv.app.features.home.HeroIndicators
import org.viptv.app.features.home.HeroProgress
import org.viptv.app.features.home.PhoneHero
import org.viptv.app.features.home.TabletHero
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HeroMotionTest {
    @get:Rule val uiThread = UiThreadRule()

    private val base = PreviewData.hero.copy(landscape = FirstArt, poster = null, logo = LogoArt)
    private val first = base
    private val second = base.copy(identity = MediaIdentity("hero-2", "movie", emptyList()), title = "Second Feature",
        landscape = SecondArt, logo = null, description = null)
    private val slow = base.copy(identity = MediaIdentity("hero-3", "movie", emptyList()), title = "Third Feature",
        landscape = SlowArt, logo = null, description = null)

    private class Shell(val scene: ImageComposeScene) : AutoCloseable {
        private var frame = 0L

        fun frames(count: Int = 1) = repeat(count) {
            scene.render(frame++ * 16_666_667L).close()
            Thread.sleep(2)
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

        fun tagged(tag: String): SemanticsNode = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

        fun labels(): String = nodes().joinToString(" ") { node ->
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        }

        fun dots(): Rect? = nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Featured ") && it.contains(" of ") } == true
        }?.boundsInRoot

        fun dotWidths(): List<Int> {
            val row = dots() ?: return emptyList()
            val pixels = image()
            val background = Palette.background.toArgb()
            val widths = mutableListOf<Int>()
            var run = 0
            for (x in row.left.toInt() until (row.left + 112f).toInt()) {
                if (distance(pixels.getColor(x, row.center.y.toInt()), background) > 6) run++ else { if (run > 0) widths += run; run = 0 }
            }
            if (run > 0) widths += run
            return widths
        }

        fun colour(rect: Rect): Int = image().getColor(rect.center.x.toInt(), rect.center.y.toInt())

        private fun image(): Bitmap = scene.render(frame++ * 16_666_667L).use { Bitmap.makeFromImage(it) }

        fun clickable(text: String): SemanticsNode = scene.semanticsOwners
            .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            .first { node -> node.config.contains(SemanticsActions.OnClick) &&
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }

        override fun close() = scene.close()
    }

    private fun carousel(card: MutableState<Card>, index: MutableState<Int>) =
        HeroCarousel(card.value, index.value, 3, HeroProgress()) {}

    private fun desktopShell(card: MutableState<Card>, index: MutableState<Int>, reduced: Boolean = false): Shell =
        Shell(ImageComposeScene(1440, 1000, Density(1f)) {
            CompositionLocalProvider(LocalReducedMotion provides reduced) {
                ViptvTheme(pointer = true) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        Column {
                            DesktopHero(card.value, {}, {}, carousel = carousel(card, index))
                            Rows()
                        }
                    }
                }
            }
        })

    private fun phoneShell(card: MutableState<Card>, index: MutableState<Int>): Shell =
        Shell(ImageComposeScene(393, 852, Density(1f)) {
            ViptvTheme(pointer = false) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    Column {
                        PhoneHero(card.value, {}, {}, false, carousel(card, index))
                        Rows()
                    }
                }
            }
        })

    private fun tabletShell(card: MutableState<Card>, index: MutableState<Int>): Shell =
        Shell(ImageComposeScene(1024, 900, Density(1f)) {
            ViptvTheme(pointer = false) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    Column {
                        TabletHero(card.value, {}, {}, {}, {}, false, carousel(card, index))
                        Rows()
                    }
                }
            }
        })

    private fun indicatorsShell(selected: MutableState<Int>, animated: Boolean): Shell =
        Shell(ImageComposeScene(400, 120, Density(1f)) {
            ViptvTheme(pointer = false) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HeroIndicators(3, selected.value, HeroProgress(), Modifier, 1f, animated)
                }
            }
        })

    private fun withLoader(hold: String? = null, block: () -> Unit) {
        val context = PlatformContext.INSTANCE
        val previous = SingletonImageLoader.get(context)
        val loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString()
                if (hold != null && url.contains(hold)) delay(10_000)
                val art = Bitmap().apply { allocN32Pixels(64, 36); erase(COLOURS[url] ?: OtherArt) }
                SuccessResult(art.asImage(), chain.request, DataSource.MEMORY)
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        try { block() } finally { loader.shutdown(); SingletonImageLoader.setUnsafe(previous) }
    }

    @Test fun aSlideChangeCrossfadesTheCopyAndLeavesTheHeroWhereItIs() = withLoader {
        val card = mutableStateOf(first)
        val index = mutableIntStateOf(0)
        val shell = desktopShell(card, index)
        try {
            shell.frames(40)
            val summary = shell.tagged(HERO_SUMMARY_TAG).boundsInRoot
            val artwork = shell.tagged(HERO_ARTWORK_TAG).boundsInRoot
            val rows = shell.tagged(ROW_TAG).boundsInRoot
            val play = shell.clickable("Play").boundsInRoot
            assertTrue(shell.labels().contains(first.title), "the featured slide owns the hero")

            card.value = second; index.value = 1
            var bothOnScreen = false
            for (step in 1..14) {
                shell.frames(1)
                val labels = shell.labels()
                if (labels.contains(first.title) && labels.contains(second.title)) bothOnScreen = true
            }
            assertTrue(bothOnScreen, "the outgoing and incoming copy crossfade instead of swapping in one frame")

            shell.frames(30)
            val labels = shell.labels()
            assertFalse(labels.contains(first.title), "the outgoing copy leaves once the crossfade ends")
            assertTrue(labels.contains(second.title))
            assertEquals(summary, shell.tagged(HERO_SUMMARY_TAG).boundsInRoot, "the hero keeps its footprint")
            assertEquals(artwork, shell.tagged(HERO_ARTWORK_TAG).boundsInRoot, "the artwork keeps its frame")
            assertEquals(rows, shell.tagged(ROW_TAG).boundsInRoot, "the rows below the hero never move")
            assertEquals(play, shell.clickable("Play").boundsInRoot, "the copy settles back where it was, without travel")
            assertTrue(near(shell.colour(artwork), COLOURS.getValue(SecondArt)), "the incoming artwork replaces the outgoing one")
        } finally { shell.close() }
    }

    @Test fun anArtworkThatHasNotDecodedKeepsThePreviousOne() = withLoader(hold = "slow") {
        val card = mutableStateOf(first)
        val index = mutableIntStateOf(0)
        val shell = desktopShell(card, index)
        try {
            shell.frames(40)
            val artwork = shell.tagged(HERO_ARTWORK_TAG).boundsInRoot
            assertTrue(near(shell.colour(artwork), COLOURS.getValue(FirstArt)), "the featured artwork is painted")

            card.value = slow; index.value = 1
            shell.frames(40)
            assertTrue(shell.labels().contains(slow.title), "the copy moved to the incoming slide")
            assertTrue(near(shell.colour(artwork), COLOURS.getValue(FirstArt)), "the artwork stays until the incoming one is decoded")
        } finally { shell.close() }
    }

    @Test fun theActiveDotGrowsIntoItsPillWhileTheOtherDotsStayDots() = withLoader {
        val card = mutableStateOf(first)
        val index = mutableIntStateOf(0)
        val shell = desktopShell(card, index)
        try {
            shell.frames(40)
            assertEquals(listOf(48, 16, 16), shell.dotWidths(), "the featured slide's dot is the pill")

            index.value = 1
            var grew = false
            for (step in 1..14) {
                shell.frames(1)
                val widths = shell.dotWidths()
                if (widths.size == 3 && widths[0] in 15..17 && widths[1] in 19..45 && widths[2] in 15..17) grew = true
            }
            assertTrue(grew, "the incoming dot grows from a dot to its pill while the others stay dots")

            shell.frames(30)
            assertEquals(listOf(16, 48, 16), shell.dotWidths(), "the grown dot settles as the pill")
        } finally { shell.close() }
    }

    @Test fun reduceMotionSnapsTheHeroAndStillMovesTheDot() = withLoader {
        val card = mutableStateOf(first)
        val index = mutableIntStateOf(0)
        val shell = desktopShell(card, index, reduced = true)
        try {
            shell.frames(40)
            val summary = shell.tagged(HERO_SUMMARY_TAG).boundsInRoot
            val artwork = shell.tagged(HERO_ARTWORK_TAG).boundsInRoot
            val rows = shell.tagged(ROW_TAG).boundsInRoot

            card.value = second; index.value = 1
            shell.frames(3)
            val labels = shell.labels()
            assertFalse(labels.contains(first.title), "reduce motion drops the outgoing copy in the same frame: $labels")
            assertTrue(labels.contains(second.title))
            assertEquals(listOf(16, 48, 16), shell.dotWidths(), "the dot still reports the next slide")
            assertEquals(summary, shell.tagged(HERO_SUMMARY_TAG).boundsInRoot, "the hero keeps its footprint")
            assertEquals(artwork, shell.tagged(HERO_ARTWORK_TAG).boundsInRoot, "the artwork keeps its frame")
            assertEquals(rows, shell.tagged(ROW_TAG).boundsInRoot, "the rows below the hero never move")
        } finally { shell.close() }
    }

    @Test fun thePhoneAndTabletHeroesCrossfadeTheirCopyAndHoldTheDotsAndActionsStill() = withLoader {
        for ((where, host) in listOf<Pair<String, (MutableState<Card>, MutableState<Int>) -> Shell>>("phone" to ::phoneShell, "tablet" to ::tabletShell)) {
            val card = mutableStateOf(first)
            val index = mutableIntStateOf(0)
            val shell = host(card, index)
            try {
                shell.frames(40)
                val dots = shell.dots()
                assertNotNull(dots, "$where: the hero draws its carousel dots")
                assertTrue(shell.labels().contains(first.title), "$where: the featured slide owns the hero")
                val play = shell.clickable("Play").boundsInRoot

                card.value = second; index.value = 1
                var bothOnScreen = false
                for (step in 1..14) {
                    shell.frames(1)
                    val labels = shell.labels()
                    if (labels.contains(first.title) && labels.contains(second.title)) bothOnScreen = true
                }
                assertTrue(bothOnScreen, "$where: the copy crossfades instead of swapping in one frame")

                shell.frames(30)
                assertTrue(shell.labels().contains(second.title), "$where: the next slide's copy owns the hero")
                assertEquals(dots, shell.dots(), "$where: the dots stay where they are while the copy changes")
                assertEquals(play, shell.clickable("Play").boundsInRoot, "$where: the actions stay anchored to the hero")
            } finally { shell.close() }
        }
    }

    @Test fun theTvIndicatorsDoNotAnimate() = withLoader {
        val selected = mutableIntStateOf(0)
        val still = indicatorsShell(selected, animated = false)
        try {
            still.frames(3)
            assertEquals(listOf(48, 16, 16), still.dotWidths())
            selected.value = 1
            still.frames(2)
            assertEquals(listOf(16, 48, 16), still.dotWidths(), "TV's dots swap in one frame")
        } finally { still.close() }

        val moving = mutableIntStateOf(0)
        val animated = indicatorsShell(moving, animated = true)
        try {
            animated.frames(3)
            moving.value = 1
            animated.frames(1)
            assertEquals(listOf(16, 16, 16), animated.dotWidths(), "the incoming dot starts as a dot before it grows")
        } finally { animated.close() }
    }

    private companion object {
        const val FirstArt = "https://art.test/first.jpg"
        const val SecondArt = "https://art.test/second.jpg"
        const val SlowArt = "https://art.test/slow.jpg"
        const val LogoArt = "https://art.test/logo.png"
        const val OtherArt = 0xFF808080.toInt()
        val COLOURS = mapOf(FirstArt to 0xFF1D3B5A.toInt(), SecondArt to 0xFF5A3B1D.toInt(), SlowArt to 0xFF1D5A3B.toInt())
    }
}

private const val ROW_TAG = "row-below-hero"

@Composable
private fun Rows() = Box(Modifier.fillMaxWidth().height(120.dp).background(Palette.raised).testTag(ROW_TAG))

private fun near(actual: Int, expected: Int) = distance(actual, expected) <= 12

private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }