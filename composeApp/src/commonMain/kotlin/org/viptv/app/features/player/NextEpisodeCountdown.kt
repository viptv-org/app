package org.viptv.app.features.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import org.viptv.engine.wire.Card

@Stable
internal class NextEpisodeCountdown(private val totalMillis: Long = 10_000) {
    private val totalSeconds = ceil(totalMillis / 1000.0).toInt()
    var secondsLeft by mutableIntStateOf(totalSeconds)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var running by mutableStateOf(false)
        private set
    val elapsedShare: Float get() = 1f - secondsLeft.toFloat() / totalSeconds
    private var stopped = false

    fun cancel() { stopped = true; running = false }

    suspend fun run(expire: () -> Unit) {
        if (stopped) return
        running = true
        val start = withFrameMillis { it }
        while (running) {
            val elapsed = withFrameMillis { it } - start
            if (!running) return
            progress = (elapsed.toFloat() / totalMillis).coerceIn(0f, 1f)
            secondsLeft = ceil((totalMillis - elapsed).coerceAtLeast(0) / 1000.0).toInt()
            if (elapsed >= totalMillis) { cancel(); expire() }
        }
    }
}

@Composable
internal fun rememberNextEpisodeCountdown(autoplay: Boolean, expire: () -> Unit): NextEpisodeCountdown {
    val countdown = remember { NextEpisodeCountdown() }
    val latestExpire by rememberUpdatedState(expire)
    LaunchedEffect(countdown, autoplay) { if (autoplay) countdown.run { latestExpire() } else countdown.cancel() }
    return countdown
}

internal fun nextEpisodeAutoplays(enabled: Boolean, title: Card?, live: Boolean): Boolean =
    enabled && !live && title != null && title.identity.episode != null && title.identity.kind !in listOf("channel", "tv")

internal fun Modifier.cancelsCountdown(countdown: NextEpisodeCountdown): Modifier =
    onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) countdown.cancel(); false }
        .pointerInput(countdown) {
            awaitPointerEventScope {
                while (true) { if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) countdown.cancel() }
            }
        }

@Composable
internal fun NextEpisodeCountdownRing(countdown: NextEpisodeCountdown, color: Color, size: Dp, stroke: Dp, reduceMotion: Boolean,
    content: @Composable () -> Unit) {
    if (!countdown.running || reduceMotion) { content(); return }
    val announced = countdown.elapsedShare
    Box(Modifier.size(size).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(announced, 0f..1f)
    }.drawBehind {
        val width = stroke.toPx()
        val inset = width / 2
        val arc = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(color.copy(alpha = .28f), 0f, 360f, false, topLeft, arc, style = Stroke(width))
        drawArc(color, -90f, 360f * (1f - countdown.progress), false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
    }, contentAlignment = Alignment.Center) { content() }
}
