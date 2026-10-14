package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.emptyFlow
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PlayerSurface

/** Explicit public-media diagnostic, independent of account, storage and tracking. */
internal fun launchDesktopPlayerQa() = application {
    val state = rememberWindowState(width = 1920.dp, height = 1080.dp)
    val input = remember { PlaybackInput("desktop-public-60fps-check",
        "https://test-streams.mux.dev/x36xhzz/url_8/193039199_mp4_h264_aac_fhd_7.m3u8",
        mimeType = "application/x-mpegURL",
        diagnostics = { System.err.println("VIPTV_DESKTOP_QA: $it") }) }
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Desktop Playback QA", state = state) {
        PlayerSurface(input, false, Modifier.fillMaxSize().background(Color.Black), observe = { fact ->
            when (fact) {
                is PlayerFact.Status -> System.err.println("VIPTV_DESKTOP_QA: state=${fact.name}")
                is PlayerFact.Presentation -> System.err.println("VIPTV_DESKTOP_QA: presentation=${fact.proof}")
                PlayerFact.Failed -> System.err.println("VIPTV_DESKTOP_QA: failed=true")
                else -> Unit
            }
        }, commands = emptyFlow())
    }
}
