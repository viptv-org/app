package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import org.viptv.app.theme.Palette

@Composable
internal fun DesktopAppWindow(onCloseRequest: () -> Unit, title: String, state: WindowState, framed: Boolean,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }, content: @Composable FrameWindowScope.() -> Unit) {
    Window(onCloseRequest = onCloseRequest, title = title, state = state, undecorated = true,
        onPreviewKeyEvent = onPreviewKeyEvent) {
        DesktopWindowFrame(framed) { content() }
    }
}

@Composable
internal fun DesktopWindowFrame(framed: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val frame = if (!framed) Modifier else Modifier
        .clip(shape)
        .background(Palette.background)
        .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
    // Paint the window's own surface underneath, or an X11 host without a compositor shows black pixels.
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Box(Modifier.fillMaxSize().then(frame)) { content() }
    }
}
