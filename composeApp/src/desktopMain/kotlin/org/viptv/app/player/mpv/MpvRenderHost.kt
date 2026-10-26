package org.viptv.app.player.mpv

import androidx.compose.runtime.*
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.Container
import java.awt.Window
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.SwingUtilities

private val LocalMpvHost = staticCompositionLocalOf<MpvRenderHost?> { null }

/** Keep raster rendering and destruction on the owning window's AWT recording thread. */
@Composable
internal fun MpvWindow(window: Window, content: @Composable () -> Unit) {
    val host = remember(window) { MpvRenderHost(window) }
    DisposableEffect(host) { host.attach(); onDispose { host.close() } }
    CompositionLocalProvider(LocalMpvHost provides host, content = content)
}

@Composable
internal fun mpvHost(): MpvRenderHost = requireNotNull(LocalMpvHost.current) { "mpv requires an owned desktop window" }

internal class MpvRenderHost(private val window: Window) {
    private val renderers = CopyOnWriteArrayList<MpvRenderer>()
    private val cleanup = ConcurrentLinkedQueue<() -> Unit>()
    private var layer: SkiaLayer? = null
    @Volatile private var closed = false
    fun attach() {
        fun find(c: Container): SkiaLayer? = (c as? SkiaLayer) ?: c.components.filterIsInstance<Container>().firstNotNullOfOrNull(::find)
        val target = requireNotNull(find(window)) { "Desktop render layer missing" }
        layer = target
        val original = requireNotNull(target.renderDelegate)
        target.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                while (true) {
                    val task = cleanup.poll() ?: break
                    task()
                }
                renderers.forEach { it.prepare() }
                original.onRender(canvas, width, height, nanoTime)
            }
        }
    }
    fun add(renderer: MpvRenderer) { renderers.add(renderer); redraw() }
    fun remove(renderer: MpvRenderer) {
        renderers.remove(renderer)
        cleanup.add { renderer.destroy() }
        redraw()
    }
    fun redraw() { SwingUtilities.invokeLater { if (!closed) layer?.needRender() } }
    fun close() {
        renderers.toList().forEach { it.close() }
        closed = true
        if (cleanup.isNotEmpty()) layer?.renderImmediately()
        layer = null
    }
}
