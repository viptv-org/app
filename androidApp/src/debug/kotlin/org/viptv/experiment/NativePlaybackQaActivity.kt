package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

/** Direct public-media qualification without accounts, providers or history writes. */
class NativePlaybackQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurePlayerWindow(true, true)
        val uri = requireNotNull(intent.getStringExtra("qa_media_uri"))
        require(android.net.Uri.parse(uri).scheme == "https")
        val input = PlaybackInput("public-playback-qa", uri,
            positionMillis = intent.getLongExtra("qa_position_ms", 0), live = intent.getBooleanExtra("qa_live", false),
            diagnostics = { Log.i("VIPTV_PLAYBACK_QA", it) })
        setContent {
            var paused by remember { mutableStateOf(false) }
            var back by remember { mutableStateOf<(() -> Unit)?>(null) }
            BackHandler { back?.invoke() ?: finish() }
            ViptvTheme(television = true) {
                PlayerScreen(input, paused, null, { paused = !paused }, { fact ->
                    when (fact) {
                        is PlayerFact.Status -> Log.i("VIPTV_PLAYBACK_QA", "status=${fact.name}")
                        is PlayerFact.Presentation -> Log.i("VIPTV_PLAYBACK_QA", "presentation=${fact.proof} exact=${fact.exact}")
                        is PlayerFact.Progress -> Log.i("VIPTV_PLAYBACK_QA", "position_ms=${fact.positionMillis} duration_ms=${fact.durationMillis}")
                        PlayerFact.Failed -> Log.w("VIPTV_PLAYBACK_QA", "failed=true")
                        else -> Unit
                    }
                }, { finish() }, title = Card(identity = MediaIdentity("public-clip", "movie", emptyList()),
                    provider_id = "public-media", title = intent.getStringExtra("qa_title") ?: "Playback qualification", badges = emptyList()),
                    television = true, backChanged = { back = it })
            }
        }
    }
}
