package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.LIST_END_COPY
import org.viptv.app.components.LIST_LOAD_MORE_COPY
import org.viptv.app.components.LIST_RETRY_COPY
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class LivePagingFooterUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun channel() = LiveChannel(Card(MediaIdentity("one", "live", emptyList()),
        provider_id = "m3u:1", title = "Fixture news", badges = emptyList()))

    @Composable
    private fun Host(television: Boolean, busy: Boolean, more: (() -> Unit)?, pageError: String?) {
        ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            LiveScreen(listOf(channel()), television, start = null, now = null, watch = {}, manage = {},
                more = more, busy = busy, pageError = pageError)
        } }
    }

    @Test fun appendedPageShowsLoadingCopyOnPhoneDesktopAndTv() {
        for ((width, height, television) in listOf(Triple(390, 844, false), Triple(1280, 800, false), Triple(1920, 1080, true))) {
            val scene = ImageComposeScene(width, height, Density(1f)) { Host(television, busy = true, more = {}, pageError = null) }
            try {
                frames(scene)
                assertTrue(nodes(scene).any { text(it) == "Loading more channels…" },
                    "${width}px ${if (television) "TV" else "app"} shows the appended-page loading copy")
            } finally { scene.close() }
        }
    }

    @Test fun completedListShowsTheEndCopyOnPhoneDesktopAndTv() {
        for ((width, height, television) in listOf(Triple(390, 844, false), Triple(1280, 800, false), Triple(1920, 1080, true))) {
            val scene = ImageComposeScene(width, height, Density(1f)) { Host(television, busy = false, more = null, pageError = null) }
            try {
                frames(scene)
                assertTrue(nodes(scene).any { text(it) == LIST_END_COPY },
                    "${width}px ${if (television) "TV" else "app"} shows the finished-list copy")
            } finally { scene.close() }
        }
    }

    @Test fun failedPageShowsRetryCopyOnPhoneDesktopAndTv() {
        for ((width, height, television) in listOf(Triple(390, 844, false), Triple(1280, 800, false), Triple(1920, 1080, true))) {
            val scene = ImageComposeScene(width, height, Density(1f)) { Host(television, busy = false, more = {}, pageError = "oops") }
            try {
                frames(scene)
                val texts = nodes(scene).map(::text)
                assertTrue(texts.contains(LIST_LOAD_MORE_COPY),
                    "${width}px ${if (television) "TV" else "app"} shows the failed-page copy")
                assertTrue(texts.contains(LIST_RETRY_COPY),
                    "${width}px ${if (television) "TV" else "app"} shows the retry affordance")
            } finally { scene.close() }
        }
    }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
