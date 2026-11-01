package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.components.TvAction
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.components.pageGutter
import org.viptv.app.components.rememberTvLoadingShimmer
import org.viptv.app.connection.AccountAccess
import org.viptv.app.connection.LiveCatalogPage
import org.viptv.app.theme.Palette

internal const val LiveEyebrow = "LIVE TV"
internal const val LiveNoSourceHeadline = "Live TV isn't set up yet."
internal const val LiveNoSourceBody = "Add an IPTV connection with Xtream or M3U, or install a live add-on. Channels, the guide and favourites appear here."
internal const val LiveSetUp = "Set up live TV"
internal const val LiveAddons = "Add-ons"
internal const val LiveMemberWho = "Ask the account owner to set up live TV."
internal const val LivePairedWho = "Manage live TV from a signed-in phone, computer or the web dashboard."
internal const val LiveNoChannelsHeadline = "No channels came back."
internal const val LiveNoChannelsBody = "This source is set up, but it isn't sending channels."
internal const val LiveTryAgain = "Try again"
internal const val LiveSourceIntegrations = "Source integrations"
internal const val LiveSourceIntegrationsSection = "Source integrations"
internal const val LiveAddonsSection = "Add-ons"

enum class LiveSpread { Channels, Filtered, Loading, NoSource, NoChannels }

// The page's own facts decide the state; a source pick is how a working second connection is reached, so it never masquerades as an empty filter.
internal fun liveSpread(page: LiveCatalogPage?, busy: Boolean, filtered: Boolean): LiveSpread = when {
    page != null && page.channels.isNotEmpty() -> LiveSpread.Channels
    filtered -> LiveSpread.Filtered
    page == null -> if (busy) LiveSpread.Loading else LiveSpread.NoChannels
    page.connections.isEmpty() || page.availability == "NeedsConfiguration" -> LiveSpread.NoSource
    busy -> LiveSpread.Loading
    else -> LiveSpread.NoChannels
}

internal fun LiveSpread.showsChannelRows(): Boolean = this == LiveSpread.Loading || this == LiveSpread.NoChannels

internal class LiveEmptyMetrics(
    val eyebrow: TextUnit, val eyebrowLine: TextUnit, val eyebrowTracking: TextUnit,
    val headline: TextUnit, val headlineLine: TextUnit, val headlineTracking: TextUnit, val headlineLines: Int,
    val body: TextUnit, val bodyLine: TextUnit, val bodyLines: Int,
    val primaryHeight: Dp, val primaryRadius: Dp, val primaryPadding: Dp, val primaryText: TextUnit, val primaryLine: TextUnit,
    val secondaryHeight: Dp, val secondaryRadius: Dp, val secondaryPadding: Dp, val secondaryText: TextUnit, val secondaryLine: TextUnit,
    val eyebrowToHeadline: Dp, val headlineToBody: Dp, val bodyToActions: Dp, val primaryToSecondary: Dp,
) {
    val actionBlock: Dp get() = primaryHeight + primaryToSecondary + secondaryHeight

    companion object {
        fun television(scale: Float) = LiveEmptyMetrics(
            eyebrow = (18 * scale).sp, eyebrowLine = (22 * scale).sp, eyebrowTracking = (1.44f * scale).sp,
            headline = (56 * scale).sp, headlineLine = (64 * scale).sp, headlineTracking = (-1.12f * scale).sp, headlineLines = 2,
            body = (26 * scale).sp, bodyLine = (38 * scale).sp, bodyLines = 3,
            primaryHeight = 72.dp * scale, primaryRadius = 36.dp * scale, primaryPadding = 34.dp * scale,
            primaryText = (26 * scale).sp, primaryLine = (32 * scale).sp,
            secondaryHeight = 52.dp * scale, secondaryRadius = 26.dp * scale, secondaryPadding = 24.dp * scale,
            secondaryText = (22 * scale).sp, secondaryLine = (28 * scale).sp,
            eyebrowToHeadline = 18.dp * scale, headlineToBody = 22.dp * scale, bodyToActions = 34.dp * scale, primaryToSecondary = 14.dp * scale)

        fun wide(compact: Boolean) = LiveEmptyMetrics(
            eyebrow = if (compact) 11.sp else 12.sp, eyebrowLine = if (compact) 14.sp else 16.sp,
            eyebrowTracking = if (compact) .88.sp else .72.sp,
            headline = if (compact) 34.sp else 40.sp, headlineLine = if (compact) 40.sp else 46.sp,
            headlineTracking = if (compact) (-.68f).sp else (-.8f).sp, headlineLines = if (compact) 3 else 2,
            body = if (compact) 15.sp else 16.sp, bodyLine = if (compact) 22.sp else 24.sp, bodyLines = 3,
            primaryHeight = if (compact) 54.dp else 48.dp, primaryRadius = if (compact) 27.dp else 24.dp,
            primaryPadding = 22.dp,
            primaryText = if (compact) 17.sp else 16.sp, primaryLine = if (compact) 22.sp else 20.sp,
            secondaryHeight = if (compact) 44.dp else 40.dp, secondaryRadius = if (compact) 22.dp else 20.dp,
            secondaryPadding = if (compact) 16.dp else 14.dp,
            secondaryText = 15.sp, secondaryLine = 20.sp,
            eyebrowToHeadline = if (compact) 12.dp else 16.dp, headlineToBody = if (compact) 16.dp else 20.dp,
            bodyToActions = if (compact) 24.dp else 28.dp, primaryToSecondary = if (compact) 10.dp else 12.dp)

        fun noChannels(television: Boolean, scale: Float, compact: Boolean) = if (television) LiveEmptyMetrics(
            eyebrow = 1.sp, eyebrowLine = 1.sp, eyebrowTracking = 0.sp,
            headline = (44 * scale).sp, headlineLine = (50 * scale).sp, headlineTracking = 0.sp, headlineLines = 2,
            body = (24 * scale).sp, bodyLine = (34 * scale).sp, bodyLines = 3,
            primaryHeight = 72.dp * scale, primaryRadius = 36.dp * scale, primaryPadding = 34.dp * scale,
            primaryText = (26 * scale).sp, primaryLine = (32 * scale).sp,
            secondaryHeight = 52.dp * scale, secondaryRadius = 26.dp * scale, secondaryPadding = 24.dp * scale,
            secondaryText = (22 * scale).sp, secondaryLine = (28 * scale).sp,
            eyebrowToHeadline = 0.dp, headlineToBody = 16.dp * scale, bodyToActions = 28.dp * scale, primaryToSecondary = 14.dp * scale)
        else LiveEmptyMetrics(
            eyebrow = 1.sp, eyebrowLine = 1.sp, eyebrowTracking = 0.sp,
            headline = if (compact) 20.sp else 28.sp, headlineLine = if (compact) 26.sp else 34.sp,
            headlineTracking = 0.sp, headlineLines = 2,
            body = if (compact) 14.sp else 15.sp, bodyLine = if (compact) 21.sp else 22.sp, bodyLines = 3,
            primaryHeight = if (compact) 54.dp else 48.dp, primaryRadius = if (compact) 27.dp else 24.dp,
            primaryPadding = 22.dp, primaryText = if (compact) 17.sp else 16.sp, primaryLine = if (compact) 22.sp else 20.sp,
            secondaryHeight = if (compact) 44.dp else 40.dp, secondaryRadius = if (compact) 22.dp else 20.dp,
            secondaryPadding = if (compact) 16.dp else 14.dp, secondaryText = 15.sp, secondaryLine = 20.sp,
            eyebrowToHeadline = 0.dp, headlineToBody = if (compact) 10.dp else 12.dp,
            bodyToActions = if (compact) 20.dp else 24.dp, primaryToSecondary = if (compact) 10.dp else 12.dp)
    }
}

internal const val LivePhoneArtTag = "live-empty-phone-art"

private val LivePhoneArtToCopy = 20.dp

@Composable
internal fun LiveNoSourcePage(television: Boolean, access: AccountAccess?, local: Boolean, returnFocus: Boolean,
    setUp: () -> Unit, addons: () -> Unit, requestAccess: () -> Unit, focusTaken: () -> Unit) {
    LaunchedEffect(Unit) { requestAccess() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = if (television) maxWidth / 1920.dp else 1f
        val compact = maxWidth < 640.dp
        val primary = remember { FocusRequester() }
        val actions = local || access?.managesSources == true
        val who = when {
            local || access == null -> null
            access.pairedDevice -> LivePairedWho
            access.member -> LiveMemberWho
            else -> null
        }
        val hadActions = remember { mutableStateOf(actions) }
        LaunchedEffect(actions, returnFocus) {
            if (television && actions && (returnFocus || !hadActions.value)) { withFrameNanos { }; runCatching { primary.requestFocus() }; focusTaken() }
            hadActions.value = actions
        }
        val metrics = if (television) LiveEmptyMetrics.television(scale) else LiveEmptyMetrics.wide(compact)
        val full = if (compact) Modifier.fillMaxWidth() else Modifier
        val gutter = pageGutter(television = false, tablet = false, browser = false, maxWidth = maxWidth)
        val text: @Composable ColumnScope.() -> Unit = {
            Text(LiveEyebrow, color = Palette.secondary, fontSize = metrics.eyebrow, lineHeight = metrics.eyebrowLine,
                letterSpacing = metrics.eyebrowTracking, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(metrics.eyebrowToHeadline))
            Text(LiveNoSourceHeadline, color = Palette.text, fontSize = metrics.headline, lineHeight = metrics.headlineLine,
                letterSpacing = metrics.headlineTracking, fontWeight = FontWeight.Bold, maxLines = metrics.headlineLines)
            Spacer(Modifier.height(metrics.headlineToBody))
            Text(LiveNoSourceBody, color = Palette.body, fontSize = metrics.body, lineHeight = metrics.bodyLine, maxLines = metrics.bodyLines)
            Spacer(Modifier.height(metrics.bodyToActions))
            when {
                actions -> {
                    LiveEmptyPrimary(television, scale, metrics, primary, setUp, full)
                    Spacer(Modifier.height(metrics.primaryToSecondary))
                    LiveEmptySecondary(television, scale, metrics, addons, full)
                }
                who != null -> Text(who, Modifier.heightIn(min = metrics.actionBlock), color = Palette.secondary,
                    fontSize = metrics.body, lineHeight = metrics.bodyLine)
                else -> Spacer(Modifier.height(metrics.actionBlock))
            }
        }
        Box(Modifier.fillMaxSize().focusGroup()) {
            if (television || !compact) LiveEmptyArtwork(true)
            when {
                television -> Column(Modifier.align(Alignment.CenterStart).padding(start = 96.dp * scale).width(840.dp * scale),
                    verticalArrangement = Arrangement.Center, content = text)
                compact -> Column(Modifier.fillMaxSize().pageBottomClearance()) {
                    LiveEmptyPhoneArt(Modifier.fillMaxWidth().weight(1f).testTag(LivePhoneArtTag))
                    Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = LivePhoneArtToCopy), content = text)
                }
                else -> Column(Modifier.align(Alignment.CenterStart).padding(start = 40.dp).widthIn(max = 620.dp), content = text)
            }
        }
    }
}

@Composable
private fun LiveEmptyPrimary(television: Boolean, scale: Float, metrics: LiveEmptyMetrics, requester: FocusRequester,
    activate: () -> Unit, modifier: Modifier) {
    if (television) TvAction(modifier.height(metrics.primaryHeight), scale, requester = requester, activate = activate,
        cornerRadius = metrics.primaryRadius, contentPadding = PaddingValues(horizontal = metrics.primaryPadding),
        background = Color.White.copy(alpha = .12f)) { color ->
        Text(LiveSetUp, color = color, fontSize = metrics.primaryText, lineHeight = metrics.primaryLine, fontWeight = FontWeight.Bold)
    } else Button(activate, modifier.height(metrics.primaryHeight), shape = RoundedCornerShape(metrics.primaryRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.background),
        contentPadding = PaddingValues(horizontal = metrics.primaryPadding)) {
        Text(LiveSetUp, fontSize = metrics.primaryText, lineHeight = metrics.primaryLine, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveEmptySecondary(television: Boolean, scale: Float, metrics: LiveEmptyMetrics, activate: () -> Unit, modifier: Modifier) {
    if (television) TvAction(modifier.height(metrics.secondaryHeight), scale, activate = activate,
        cornerRadius = metrics.secondaryRadius, contentPadding = PaddingValues(horizontal = metrics.secondaryPadding),
        background = Color.White.copy(alpha = .12f)) { color ->
        Text(LiveAddons, color = color, fontSize = metrics.secondaryText, lineHeight = metrics.secondaryLine, fontWeight = FontWeight.SemiBold)
    } else TextButton(activate, modifier.height(metrics.secondaryHeight), contentPadding = PaddingValues(horizontal = metrics.secondaryPadding)) {
        Text(LiveAddons, color = Palette.secondary, fontSize = metrics.secondaryText, lineHeight = metrics.secondaryLine, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun LiveNoChannels(television: Boolean, scale: Float, compact: Boolean, reason: String?, retry: (() -> Unit)?, integrations: (() -> Unit)?) {
    val metrics = LiveEmptyMetrics.noChannels(television, scale, compact)
    val full = if (compact && !television) Modifier.fillMaxWidth() else Modifier
    Column(Modifier.fillMaxWidth()) {
        Text(LiveNoChannelsHeadline, color = Palette.text, fontSize = metrics.headline, lineHeight = metrics.headlineLine,
            fontWeight = FontWeight.Bold, maxLines = metrics.headlineLines)
        Spacer(Modifier.height(metrics.headlineToBody))
        Text(reason ?: LiveNoChannelsBody, color = Palette.secondary, fontSize = metrics.body, lineHeight = metrics.bodyLine, maxLines = metrics.bodyLines)
        retry?.let {
            Spacer(Modifier.height(metrics.bodyToActions))
            if (television) TvAction(full.height(metrics.primaryHeight), scale, activate = it, cornerRadius = metrics.primaryRadius,
                contentPadding = PaddingValues(horizontal = metrics.primaryPadding), background = Color.White.copy(alpha = .12f)) { color ->
                Text(LiveTryAgain, color = color, fontSize = metrics.primaryText, lineHeight = metrics.primaryLine, fontWeight = FontWeight.Bold)
            } else Button(it, full.height(metrics.primaryHeight), shape = RoundedCornerShape(metrics.primaryRadius),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.background),
                contentPadding = PaddingValues(horizontal = metrics.primaryPadding)) {
                Text(LiveTryAgain, fontSize = metrics.primaryText, lineHeight = metrics.primaryLine, fontWeight = FontWeight.Bold)
            }
        }
        integrations?.let {
            Spacer(Modifier.height(if (retry != null) metrics.primaryToSecondary else metrics.bodyToActions))
            if (television) TvAction(full.height(metrics.secondaryHeight), scale, activate = it, cornerRadius = metrics.secondaryRadius,
                contentPadding = PaddingValues(horizontal = metrics.secondaryPadding), background = Color.White.copy(alpha = .12f)) { color ->
                Text(LiveSourceIntegrations, color = color, fontSize = metrics.secondaryText, lineHeight = metrics.secondaryLine, fontWeight = FontWeight.SemiBold)
            } else TextButton(it, full.height(metrics.secondaryHeight), contentPadding = PaddingValues(horizontal = metrics.secondaryPadding)) {
                Text(LiveSourceIntegrations, color = Palette.secondary, fontSize = metrics.secondaryText, lineHeight = metrics.secondaryLine, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun LiveChannelSkeletons(television: Boolean, scale: Float, compact: Boolean) {
    val shimmer = rememberTvLoadingShimmer(scale)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (television) 8.dp * scale else 0.dp)) {
        repeat(6) {
            if (television) {
                Row(Modifier.fillMaxWidth().height(72.dp * scale).clip(RoundedCornerShape(10.dp * scale))
                    .background(Palette.surface).then(shimmer).padding(horizontal = 18.dp * scale),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                    LiveSkeletonBar(50.dp * scale, 24.dp * scale)
                    LiveSkeletonBar(300.dp * scale, 24.dp * scale)
                }
            } else Row(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(64.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).background(Palette.surface).then(shimmer))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveSkeletonBar(if (compact) 150.dp else 220.dp, 12.dp)
                    LiveSkeletonBar(if (compact) 96.dp else 160.dp, 10.dp)
                }
            }
        }
    }
}

@Composable
private fun LiveSkeletonBar(width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(height / 2)).background(Color.White.copy(alpha = .10f)))
}