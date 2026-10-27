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

/**
 * The end panel's autoplay (app#65): ten seconds, then the next episode plays through the
 * same path as its button. Time comes from the frame clock, so a test drives it with frame
 * times instead of sleeping. Once stopped (cancelled or expired) it never runs again.
 */
@Stable
internal class NextEpisodeCountdown(private val totalMillis: Long = 10_000) {
    private val totalSeconds = ceil(totalMillis / 1000.0).toInt()
    /** Whole seconds left: "Next episode in 9". */
    var secondsLeft by mutableIntStateOf(totalSeconds)
        private set
    /** 0 when it starts, 1 at expiry. Read it while drawing, so a tick redraws without recomposing. */
    var progress by mutableFloatStateOf(0f)
        private set
    var running by mutableStateOf(false)
        private set
    /** The share of whole seconds gone, for assistive technology: it changes once a second, not every frame. */
    val elapsedShare: Float get() = 1f - secondsLeft.toFloat() / totalSeconds
    private var stopped = false

    /** Any viewer action: the panel stays, autoplay doesn't come back for this end. */
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

/**
 * One countdown per opened panel. [autoplay] is the setting while the media stays ended:
 * false from the start shows no countdown, and turning false (a seek leaves the end) cancels it.
 */
@Composable
internal fun rememberNextEpisodeCountdown(autoplay: Boolean, expire: () -> Unit): NextEpisodeCountdown {
    val countdown = remember { NextEpisodeCountdown() }
    val latestExpire by rememberUpdatedState(expire)
    LaunchedEffect(countdown, autoplay) { if (autoplay) countdown.run { latestExpire() } else countdown.cancel() }
    return countdown
}

/** Only a series episode on demand counts down: never a movie, live media, or a finale (no next episode, no panel). */
internal fun nextEpisodeAutoplays(enabled: Boolean, title: Card?, live: Boolean): Boolean =
    enabled && !live && title != null && title.identity.episode != null && title.identity.kind !in listOf("channel", "tv")

/** Any key press or pointer press over the panel cancels; hover and focus placement don't. */
internal fun Modifier.cancelsCountdown(countdown: NextEpisodeCountdown): Modifier =
    onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) countdown.cancel(); false }
        .pointerInput(countdown) {
            awaitPointerEventScope {
                while (true) { if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) countdown.cancel() }
            }
        }

/** A ring around [content] that empties as the countdown runs out; with reduced motion, or once stopped, only [content] shows. */
@Composable
internal fun NextEpisodeCountdownRing(countdown: NextEpisodeCountdown, color: Color, size: Dp, stroke: Dp, reduceMotion: Boolean,
    content: @Composable () -> Unit) {
    if (!countdown.running || reduceMotion) { content(); return }
    // Read here so the semantics follow each second; the ring itself redraws every frame from [NextEpisodeCountdown.progress].
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
