package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.viptv.app.components.LocalPendingAction
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PendingPlaybackUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val card = ContinueWatchingHost.card(1)
    private val key = cardLookupKey(card)

    private fun labels(scene: ImageComposeScene): List<String> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    private fun frames(scene: ImageComposeScene, count: Int = 8) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun save(scene: ImageComposeScene, path: String) {
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path).writeBytes(it.bytes) } }
    }

    @Test fun pendingContinueWatchingCardRendersOnPhoneAndTv() {
        val phone = ImageComposeScene(390, 520, Density(1f)) {
            ViptvTheme(pointer = true) {
                CompositionLocalProvider(LocalPendingAction provides key) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        MediaCard(card, width = 171, open = {}, select = {})
                    }
                }
            }
        }
        try {
            frames(phone)
            assertTrue(labels(phone).contains("Starting playback"), labels(phone).toString())
            save(phone, "/tmp/viptv-682-pending-card-390.png")
        } finally { phone.close() }

        val tv = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                CompositionLocalProvider(LocalPendingAction provides key) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        TvCatalogCard(card, scale = 1f, portrait = false, focused = {}, open = {}, artworkWidth = 384.dp)
                    }
                }
            }
        }
        try {
            frames(tv)
            assertTrue(labels(tv).contains("Starting playback"), labels(tv).toString())
            save(tv, "/tmp/viptv-682-pending-card-tv.png")
        } finally { tv.close() }
    }
}