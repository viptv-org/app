package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.*
import org.viptv.engine.wire.SubtitleCandidate
import java.nio.file.Files
import java.nio.file.Path

internal fun launchDesktopPlayerQa() = application {
    val state = rememberWindowState(width = 1280.dp, height = 720.dp)
    val controls = System.getenv("VIPTV_DESKTOP_QA_CONTROLS") == "1"
    val scripted = System.getenv("VIPTV_DESKTOP_QA_SCRIPTED") == "1"
    check(!controls || !scripted) { "Interactive controls and scripted transport have separate owners" }
    val probe = remember { DesktopPlaybackProbe(System.getenv("VIPTV_DESKTOP_QA_RESULT")?.let(Path::of)) }
    val commands = remember { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8) }
    var paused by remember { mutableStateOf(System.getenv("VIPTV_DESKTOP_QA_PAUSED") == "1") }
    val input = remember {
        val caption = System.getenv("VIPTV_DESKTOP_QA_SUBTITLE")?.let(Path::of)
        check(!scripted || caption != null) { "Scripted desktop QA requires an owned caption fixture" }
        val uri = System.getenv("VIPTV_DESKTOP_QA_URI")
            ?: "https://test-streams.mux.dev/x36xhzz/url_8/193039199_mp4_h264_aac_fhd_7.m3u8"
        check(!scripted || System.getenv("VIPTV_DESKTOP_QA_URI") != null) { "Scripted QA requires an explicit owned media fixture" }
        PlaybackInput("desktop-owned-check", uri,
            positionMillis = System.getenv("VIPTV_DESKTOP_QA_START_MS")?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
            providerSubtitles = if (caption != null) listOf(SubtitleCandidate("desktop-fixture", "fixture", "en", "Owned fixture caption")) else emptyList(),
            subtitleContent = caption?.let { file -> { _: String -> Files.readAllBytes(file) } },
            preferences = ProfilePreferences(preferred_subtitle_language = if (caption != null) "en" else null,
                subtitle_font_scale = System.getenv("VIPTV_DESKTOP_QA_SCALE")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(.5, 2.0) ?: 1.0,
                subtitle_text_color = System.getenv("VIPTV_DESKTOP_QA_TEXT_COLOR") ?: "#FFFFFF",
                subtitle_background_color = System.getenv("VIPTV_DESKTOP_QA_BACKGROUND_COLOR") ?: "#00000000"),
            diagnostics = probe::diagnostic)
    }
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Desktop Playback QA", state = state) {
        org.viptv.app.player.mpv.MpvWindow(window) {
        if (controls) org.viptv.app.theme.ViptvTheme {
            org.viptv.app.features.player.PlayerScreen(input, paused, null, { paused = !paused }, probe::observe, ::exitApplication)
        } else PlayerSurface(input, paused, Modifier.fillMaxSize().background(Color.Black), observe = probe::observe, commands = commands)
        LaunchedEffect(input) { if (scripted) { probe.run(input, commands) { paused = it }; exitApplication() } }
    }
        }
}
