package org.viptv.app.player

import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import org.viptv.app.connection.ProfilePreferences

// One style source for PlayerView's own subtitle view and the sidecar layers bound beside it.
@UnstableApi
internal class Media3CaptionStyle(preferences: ProfilePreferences) {
    private var preferences = preferences
    private val layers = mutableSetOf<SubtitleView>()

    var scale = preferences.subtitle_font_scale.toFloat().takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
        private set

    fun attach(view: SubtitleView?) {
        if (view == null) return
        layers += view
        apply(view)
    }

    fun detach(view: SubtitleView?) {
        if (view != null) layers -= view
    }

    fun size(value: Float) {
        scale = value.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
        layers.forEach { apply(it) }
    }

    fun background(value: String?) {
        preferences = preferences.copy(subtitle_background_color = value ?: CAPTION_NO_BOX)
        layers.forEach { apply(it) }
    }

    private fun apply(view: SubtitleView) {
        val text = captionTextArgb(preferences.subtitle_text_color)
        val box = captionBoxArgb(preferences.subtitle_background_color)
        // Defaults keep authored ASS/WebVTT styling; explicit colours override embedded ones and positioning stays with each cue.
        view.setApplyEmbeddedStyles(text == CAPTION_WHITE && box == null)
        view.setApplyEmbeddedFontSizes(scale == 1f)
        view.setStyle(CaptionStyleCompat(text, box ?: Color.TRANSPARENT, Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, null))
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    }
}
