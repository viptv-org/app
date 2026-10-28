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

/**
 * The VIPTV desktop window, shared by the app and the UI preview: undecorated
 * (the app draws its own title bar) and framed by [DesktopWindowFrame].
 *
 * The window is opaque on purpose (app#92). It used to be made see-through by
 * setting AWT backgrounds behind Compose's back, so the render layer was never
 * told; on Linux Skiko then put only the first frame on screen, and hover, like
 * every later repaint, never showed. The frame paints every pixel anyway (NAV-15),
 * so a transparent window bought nothing.
 */
@Composable
internal fun DesktopAppWindow(onCloseRequest: () -> Unit, title: String, state: WindowState, framed: Boolean,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }, content: @Composable FrameWindowScope.() -> Unit) {
    Window(onCloseRequest = onCloseRequest, title = title, state = state, undecorated = true,
        onPreviewKeyEvent = onPreviewKeyEvent) {
        DesktopWindowFrame(framed) { content() }
    }
}

/**
 * Native app framing: a rounded surface with a hairline outline, which is what
 * makes an undecorated window read as a native app instead of a pasted-on
 * panel. Unframed ([framed] false) is the plain surface: macOS already rounds
 * and outlines its own windows, and the phone preview is no desktop window.
 */
@Composable
internal fun DesktopWindowFrame(framed: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val frame = if (!framed) Modifier else Modifier
        .clip(shape)
        .background(Palette.background)
        .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
    // NAV-15: paint the window's own surface underneath, so no unpainted pixel
    // can go black on an X11 host without a compositor.
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Box(Modifier.fillMaxSize().then(frame)) { content() }
    }
}
