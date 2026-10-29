package org.viptv.app.player

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import org.viptv.app.connection.ProfilePreferences

@UnstableApi
internal fun SubtitleView.profileStyle(preferences: ProfilePreferences, scale: Float) {
    val foreground = runCatching { Color.parseColor(preferences.subtitle_text_color) }.getOrDefault(Color.WHITE)
    val background = runCatching { Color.parseColor(preferences.subtitle_background_color) }.getOrDefault(Color.TRANSPARENT)
    // Defaults keep authored ASS/WebVTT styling; explicit colours override embedded ones and positioning stays with each cue.
    setApplyEmbeddedStyles(foreground == Color.WHITE && background == Color.TRANSPARENT)
    setApplyEmbeddedFontSizes(scale == 1f)
    setStyle(CaptionStyleCompat(foreground, background, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null))
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
}
