package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.Test

/** Throwaway: renders main's layout (root padded with safeDrawing over Palette.background) for the PR's "before". */
@OptIn(ExperimentalComposeUiApi::class)
class PlayerCutoutBeforeCapture {
    @Test fun before() {
        val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
        val parent = MediaIdentity("private-show", "series", emptyList())
        val card = Card(MediaIdentity("private-24", "series", emptyList(), EpisodeCoordinates("aired", 1, 24)),
            metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode 24", badges = emptyList(),
            parent_identity = parent, metadata_origin = origin)
        for ((side, insets) in listOf("left" to WindowInsets(left = 48.dp, bottom = 16.dp), "right" to WindowInsets(right = 48.dp, bottom = 16.dp))) {
            val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background).windowInsetsPadding(insets)) {
                    PlayerScreen(null, true, null, {}, {}, {}, title = card, phone = true, preview = PlayerFact.Progress(842_000, 3_132_000),
                        controlInsets = WindowInsets(0))
                }
            } }
            repeat(16) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            File("/tmp/app9-captures").mkdirs()
            scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File("/tmp/app9-captures/before-phone-player-cutout-$side.png").writeBytes(it.bytes) } }
            scene.close()
        }
    }
}
