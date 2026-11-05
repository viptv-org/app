package org.viptv.app.features.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.composables.icons.lucide.*
import org.viptv.app.components.BackControl
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberArriveFade
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal enum class PlayerFailureLayout { Phone, Desktop, Tv }

internal const val ChooseAnotherSource = "Choose another source"

internal class PlayerFailureAction(val label: String, val icon: ImageVector?, val activate: () -> Unit)

// The reason the player reports wins; otherwise the page says the one thing the viewer can do, and where playback resumes when it can.
internal fun failureDescription(reason: String?, positionMillis: Long): String = reason
    ?: if (positionMillis > 0) "$ChooseAnotherSource to continue from ${playbackTime(positionMillis)}." else "$ChooseAnotherSource."

internal data class PlayerFailureStyle(
    val layout: PlayerFailureLayout,
    val scale: Float = 1f,
    val scrim: Float,
    val icon: Dp,
    val iconDisc: Dp? = null,
    val titleSize: TextUnit,
    val titleLine: TextUnit,
    val titleTracking: TextUnit = 0.sp,
    val titleHeight: Dp? = null,
    val textSize: TextUnit,
    val textLine: TextUnit,
    val textLines: Int = Int.MAX_VALUE,
    val gap: Dp,
    val actionTop: Dp = 0.dp,
    val actionGap: Dp,
    val actionHeight: Dp,
    val actionWidth: Dp? = null,
    val backWidth: Dp? = null,
    val actionText: TextUnit,
    val actionLine: TextUnit,
    val actionIcon: Dp,
    val actionIconGap: Dp,
    val actionPadding: Dp,
    val actionShadow: Dp? = null,
    val actionLift: Dp = 0.dp,
    val maxWidth: Dp,
    val corner: Dp,
    val header: Boolean = false,
    val backInRow: Boolean = true,
) {
    fun retryAction(fallbackLabel: String?, activate: () -> Unit) =
        PlayerFailureAction(fallbackLabel?.let { "Try $it" } ?: "Retry", Lucide.RefreshCw, activate)

    fun chooseAction(activate: () -> Unit) = PlayerFailureAction(ChooseAnotherSource, Lucide.SlidersHorizontal, activate)
}

internal val PhoneFailureStyle = PlayerFailureStyle(PlayerFailureLayout.Phone, scrim = .8f, icon = 32.dp,
    titleSize = 22.sp, titleLine = 28.sp, textSize = 13.sp, textLine = 19.sp, textLines = 3,
    gap = 16.dp, actionGap = 10.dp, actionHeight = 44.dp, actionText = 14.sp, actionLine = 20.sp,
    actionIcon = 18.dp, actionIconGap = 8.dp, actionPadding = 16.dp, maxWidth = 650.dp, corner = 12.dp,
    header = true, backInRow = false)

internal val DesktopFailureStyle = PlayerFailureStyle(PlayerFailureLayout.Desktop, scrim = .82f, icon = 40.dp,
    titleSize = 26.sp, titleLine = 30.sp, titleTracking = (-.52).sp, textSize = 15.sp, textLine = 22.sp,
    gap = 12.dp, actionTop = 8.dp, actionGap = 10.dp, actionHeight = 40.dp, actionText = 14.sp, actionLine = 20.sp,
    actionIcon = 17.dp, actionIconGap = 7.dp, actionPadding = 16.dp, maxWidth = 860.dp, corner = 12.dp)

internal fun tvFailureStyle(scale: Float) = PlayerFailureStyle(PlayerFailureLayout.Tv, scale, scrim = .85f, icon = 44.dp * scale,
    iconDisc = 88.dp * scale, titleSize = (48 * scale).sp, titleLine = (55.2f * scale).sp, titleTracking = (-.96f * scale).sp,
    titleHeight = 55.2.dp * scale, textSize = (28 * scale).sp, textLine = (40.6f * scale).sp,
    gap = 18.dp * scale, actionTop = 18.dp * scale, actionGap = 16.dp * scale, actionHeight = 72.dp * scale,
    actionWidth = 380.dp * scale, backWidth = 200.dp * scale, actionText = (26 * scale).sp, actionLine = (37.7f * scale).sp,
    actionIcon = 28.dp * scale, actionIconGap = 12.dp * scale, actionPadding = 0.dp,
    actionShadow = 50.dp * scale, actionLift = 18.dp * scale, maxWidth = 1400.dp * scale, corner = 12.dp * scale)

@Composable
internal fun PlayerFailurePage(style: PlayerFailureStyle, headline: String, reason: String?, positionMillis: Long = 0,
    primary: PlayerFailureAction? = null, choose: PlayerFailureAction? = null, back: (() -> Unit)? = null,
    title: Card? = null, insets: WindowInsets = WindowInsets(0, 0, 0, 0)) {
    val initial = remember { FocusRequester() }
    val reveal = if (style.layout == PlayerFailureLayout.Tv) 1f else rememberArriveFade()
    Box(Modifier.fillMaxSize().alpha(reveal).background(Color.Black.copy(alpha = style.scrim)).windowInsetsPadding(insets)) {
        if (style.header && back != null) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BackControl(back, Modifier.then(if (primary == null && choose == null) Modifier.focusRequester(initial) else Modifier))
            Column(Modifier.weight(1f)) {
                title?.let { Text(it.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                title?.subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = style.maxWidth).padding(horizontal = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(style.gap)) {
            FailureAlert(style)
            Text(headline, Modifier.then(style.titleHeight?.let { height ->
                Modifier.height(height).wrapContentHeight(Alignment.CenterVertically, unbounded = true) } ?: Modifier),
                fontSize = style.titleSize, lineHeight = style.titleLine, letterSpacing = style.titleTracking,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(failureDescription(reason, positionMillis), fontSize = style.textSize, lineHeight = style.textLine, color = Palette.secondary,
                textAlign = TextAlign.Center, maxLines = style.textLines, overflow = TextOverflow.Ellipsis)
            FailureActions(style, primary, choose, back, initial)
        }
    }
    if (primary != null || choose != null || back != null) LaunchedEffect(Unit) { initial.requestFocus() }
}

@Composable
private fun FailureAlert(style: PlayerFailureStyle) {
    if (style.iconDisc == null) Icon(Lucide.CircleAlert, null, Modifier.size(style.icon))
    else Box(Modifier.size(style.iconDisc).background(Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Lucide.CircleAlert, null, Modifier.size(style.icon), tint = Palette.text)
    }
}

private enum class FailureActionKind { Primary, Choose, Back }

@Composable
private fun FailureActions(style: PlayerFailureStyle, primary: PlayerFailureAction?, choose: PlayerFailureAction?,
    back: (() -> Unit)?, initial: FocusRequester) {
    val actions: @Composable () -> Unit = {
        primary?.let { FailureAction(style, FailureActionKind.Primary, it, requester = initial) }
        choose?.let { FailureAction(style, FailureActionKind.Choose, it, requester = if (primary == null) initial else null) }
        if (style.backInRow && back != null) FailureAction(style, FailureActionKind.Back, PlayerFailureAction("Back", null, back),
            requester = if (primary == null && choose == null) initial else null)
    }
    if (style.layout == PlayerFailureLayout.Tv)
        Row(Modifier.padding(top = style.actionTop), horizontalArrangement = Arrangement.spacedBy(style.actionGap)) { actions() }
    else FlowRow(Modifier.padding(top = style.actionTop).wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(style.actionGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(style.actionGap)) { actions() }
}

@Composable
private fun FailureAction(style: PlayerFailureStyle, kind: FailureActionKind, action: PlayerFailureAction, requester: FocusRequester?) {
    val width = if (kind == FailureActionKind.Back) style.backWidth else style.actionWidth
    val size = Modifier.height(style.actionHeight).then(width?.let { Modifier.width(it) } ?: Modifier)
    val ink: @Composable (Color) -> Unit = { color ->
        action.icon?.let { Icon(it, null, Modifier.size(style.actionIcon), tint = color); Spacer(Modifier.width(style.actionIconGap)) }
        Text(action.label, color = color, fontSize = style.actionText, lineHeight = style.actionLine,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (style.layout == PlayerFailureLayout.Tv) {
        val modifier = size.then(style.actionShadow?.let { radius ->
            Modifier.dropShadow(RoundedCornerShape(style.corner), Shadow(radius = radius,
                color = Color.Black.copy(alpha = .45f), offset = DpOffset(0.dp, style.actionLift))) } ?: Modifier)
        if (kind == FailureActionKind.Primary) TvAction(modifier, style.scale, primary = true, requester = requester,
            activate = action.activate) { color -> ink(color) }
        else TvGlassAction(modifier, style.scale, requester = requester, activate = action.activate,
            background = Color.White.copy(alpha = .16f)) { color -> ink(color) }
    } else {
        val shape = RoundedCornerShape(style.corner)
        val modifier = size.then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
        val padding = PaddingValues(horizontal = style.actionPadding)
        val content: @Composable () -> Unit = { ink(LocalContentColor.current) }
        when {
            kind == FailureActionKind.Primary -> Button(action.activate, modifier, shape = shape, contentPadding = padding,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { content() }
            kind == FailureActionKind.Back -> OutlinedButton(action.activate, modifier, shape = shape, contentPadding = padding,
                border = BorderStroke(1.dp, Color.White.copy(alpha = .14f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)) { content() }
            else -> Button(action.activate, modifier, shape = shape, contentPadding = padding,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) { content() }
        }
    }
}
