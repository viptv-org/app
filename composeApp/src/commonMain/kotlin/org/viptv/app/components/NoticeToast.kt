package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.viptv.app.state.AppNotice
import org.viptv.app.state.NoticeAction
import org.viptv.app.theme.Palette

internal const val NOTICE_MILLIS = 5_000L
internal const val NOTICE_WITH_UNDO_MILLIS = 8_000L
internal const val NOTICE_UNDO_LABEL = "Undo"

internal fun noticeMillis(notice: AppNotice): Long =
    if (notice.action != null) NOTICE_WITH_UNDO_MILLIS else NOTICE_MILLIS

internal class TvNoticeFocus {
    val requester = FocusRequester()
    val focused = mutableStateOf(false)
}

// components.md §9: P above the nav, D bottom-centre 24 from the edge, T top-centre; surface-3, radius 20.
@Composable
internal fun NoticeToast(notice: AppNotice, television: Boolean, phone: Boolean, aboveNav: Boolean,
    undo: () -> Unit, dismiss: () -> Unit, focus: TvNoticeFocus? = null,
    durationMillis: Long = noticeMillis(notice)) {
    LaunchedEffect(notice.serial) {
        delay(durationMillis)
        dismiss()
    }
    if (television) TvNotice(notice, undo, dismiss, focus) else NoticeCard(notice, phone, aboveNav, undo)
}

@Composable
private fun NoticeCard(notice: AppNotice, phone: Boolean, aboveNav: Boolean, undo: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = if (phone && aboveNav) 116.dp else 24.dp),
        contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.widthIn(max = if (phone) 358.dp else 560.dp).fillMaxWidth().testTag("notice-toast"),
            RoundedCornerShape(20.dp), color = Palette.surface3, contentColor = Palette.text, shadowElevation = 20.dp) {
            Row(Modifier.padding(start = 18.dp, end = if (notice.action != null) 8.dp else 18.dp, top = 14.dp, bottom = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(notice.text, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2)
                if (notice.action == NoticeAction.Undo) NoticeUndo(undo)
            }
        }
    }
}

@Composable
private fun NoticeUndo(undo: () -> Unit) {
    TextButton(onClick = undo, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Text(NOTICE_UNDO_LABEL, color = Palette.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvNotice(notice: AppNotice, undo: () -> Unit, dismiss: () -> Unit, focus: TvNoticeFocus?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        // The TV top navigation owns the band above 96, so the notice sits under it where the banner does.
        Row(Modifier.align(Alignment.TopCenter).padding(top = 96.dp * scale).widthIn(max = 980.dp * scale).testTag("notice-toast")
            .clip(RoundedCornerShape(20.dp * scale)).background(Palette.surface3)
            .padding(start = 34.dp * scale, end = if (notice.action != null) 12.dp * scale else 34.dp * scale,
                top = 20.dp * scale, bottom = 20.dp * scale)
            .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            Text(notice.text, Modifier.weight(1f), fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp,
                color = Palette.text, maxLines = 2)
            if (notice.action == NoticeAction.Undo) TvAction(Modifier.heightIn(min = 64.dp * scale)
                .onFocusChanged { focus?.focused?.value = it.isFocused },
                scale, requester = focus?.requester,
                contentPadding = PaddingValues(horizontal = 24.dp * scale), activate = { undo(); dismiss() }) {
                Text(NOTICE_UNDO_LABEL, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
