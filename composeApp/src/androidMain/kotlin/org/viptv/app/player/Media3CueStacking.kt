package org.viptv.app.player

import android.text.Layout
import android.text.SpannableStringBuilder
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView

/** Every subtitle path draws through this, so simultaneous cues never cover each other. */
@UnstableApi
internal fun SubtitleView.setStackedCues(cues: List<Cue>) = setCues(stackedCues(cues))

/**
 * Media3 places each bottom cue on its own: every SubRip cue on the same bottom
 * line, and each WebVTT auto-line cue exactly one line above the previous cue
 * whatever its height. Simultaneous cues (two speakers, or a cue that wraps)
 * then draw over each other and only one line reads (app#43). Cues that share
 * the default bottom layout become one cue, so the text layout stacks and wraps
 * every line. The newest cue sits on top, as in mpv and browsers. Bottom cues
 * all keep SubtitleView's bottom padding: WebVTT's line -1 otherwise puts the
 * last line on the screen's bottom edge, under a phone's gesture bar. Cues with
 * an authored position keep their own place. WebVTT's automatic lines are -1,
 * -2 … one per simultaneous cue; a lower authored line number counts as authored.
 */
@UnstableApi
internal fun stackedCues(cues: List<Cue>): List<Cue> {
    val flows = { cue: Cue -> flowsFromBottom(cue, cues.size) }
    val groups = cues.filter(flows).groupBy(::BottomLayout)
    if (groups.isEmpty()) return cues
    val placed = mutableSetOf<BottomLayout>()
    // An identical cue repeated (e.g. by two HLS WebVTT segments) is one cue, not two lines.
    return cues.mapNotNull { cue -> if (!flows(cue)) cue
        else BottomLayout(cue).takeIf(placed::add)?.let { layout -> merge(groups.getValue(layout).distinct()) } }
}

private fun flowsFromBottom(cue: Cue, simultaneous: Int) = cue.text != null && cue.bitmap == null && cue.verticalType == Cue.TYPE_UNSET &&
    (cue.line == Cue.DIMEN_UNSET || cue.lineType == Cue.LINE_TYPE_NUMBER && cue.line < 0 && cue.line >= -simultaneous)

/** Cues merge only when the merged cue draws each of them exactly as before, apart from its height. */
private data class BottomLayout(val alignment: Layout.Alignment?, val position: Float, val positionAnchor: Int, val size: Float,
    val textSize: Float, val textSizeType: Int, val window: Int?) {
    constructor(cue: Cue) : this(cue.textAlignment, cue.position, cue.positionAnchor, cue.size, cue.textSize,
        cue.textSizeType, cue.windowColor.takeIf { cue.windowColorSet })
}

/** Media3's order is oldest first; a cue placed lower (WebVTT line -1 is the bottom line) is older. */
private fun merge(group: List<Cue>): Cue {
    val bottomUp = group.sortedBy { if (it.line == Cue.DIMEN_UNSET) 1f else -it.line }
    val cue = bottomUp.first().buildUpon().setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
    if (group.size > 1) cue.setText(SpannableStringBuilder().apply {
        bottomUp.asReversed().forEachIndexed { index, part -> if (index > 0) append('\n'); append(part.text) }
    })
    return cue.build()
}
