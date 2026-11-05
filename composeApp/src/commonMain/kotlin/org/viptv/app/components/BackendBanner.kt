package org.viptv.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import org.viptv.app.format.clockTimeOfDay
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.viptvDisplayFamily

internal const val BACKEND_BANNER_TITLE = "Can't reach the backend"

internal fun backendBannerBody(retrySeconds: Int): String =
    "The backend is down or unreachable. Retrying in $retrySeconds seconds; this clears itself once the backend answers."

internal fun backendUnreachableLabel(unreachableSince: Long): String =
    "Backend unreachable since ${clockTimeOfDay(unreachableSince)}."

internal class TvBannerFocus {
    val requester = FocusRequester()
    val focused = mutableStateOf(false)
}

internal val LocalTvBannerFocus = compositionLocalOf<TvBannerFocus?> { null }

private val IconGround = Color(0xFF2A2A2E)

internal fun backendBannerScreen(screen: Screen): Boolean = screen !in setOf(
    Screen.Connect, Screen.RecoveryCodes, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit, Screen.Player)

@Composable
internal fun BackendBanner(television: Boolean, phone: Boolean, aboveNav: Boolean, unreachableSince: Long, retrySeconds: Int,
    dismiss: () -> Unit, focus: TvBannerFocus? = null) {
    if (television) TvBackendBanner(focus, unreachableSince, retrySeconds, dismiss)
    else BackendBannerCard(phone, aboveNav, unreachableSince, retrySeconds, dismiss)
}

@Composable
private fun BackendBannerCard(phone: Boolean, aboveNav: Boolean, unreachableSince: Long, retrySeconds: Int, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = if (phone && aboveNav) 116.dp else 24.dp),
        contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.widthIn(max = if (phone) 358.dp else 520.dp).fillMaxWidth().testTag("backend-banner"),
            RoundedCornerShape(20.dp), color = Palette.surface, contentColor = Palette.text,
            border = BorderStroke(1.dp, Hairline), shadowElevation = 20.dp) {
            Row(Modifier.padding(18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).background(IconGround, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Lucide.CloudOff, null, Modifier.size(20.dp), tint = Palette.text)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(BACKEND_BANNER_TITLE, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                    Text(backendBannerBody(retrySeconds), color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
                    Text(backendUnreachableLabel(unreachableSince), color = Palette.tertiary, fontSize = 13.sp, lineHeight = 18.sp)
                    Box(Modifier.padding(top = 6.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).background(IconGround)
                        .pointerClickable(role = Role.Button, shape = RoundedCornerShape(20.dp), onClick = dismiss)
                        .padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                        Text("Dismiss", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvBackendBanner(focus: TvBannerFocus?, unreachableSince: Long, retrySeconds: Int, dismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.align(Alignment.TopCenter).padding(top = 96.dp * scale).width(980.dp * scale)
            .clip(RoundedCornerShape(28.dp * scale)).background(Palette.surface)
            .padding(horizontal = 60.dp * scale, vertical = 54.dp * scale).testTag("backend-banner")
            .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            Box(Modifier.size(72.dp * scale).background(Color.White.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Lucide.CloudOff, null, Modifier.size(34.dp * scale), tint = Palette.text)
            }
            Text(BACKEND_BANNER_TITLE, color = Palette.text, fontFamily = viptvDisplayFamily(),
                fontSize = (44 * scale).sp, lineHeight = (52 * scale).sp, fontWeight = FontWeight.Bold)
            Text(backendBannerBody(retrySeconds), color = Palette.body, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
            Text(backendUnreachableLabel(unreachableSince), color = Palette.tertiary, fontSize = (22 * scale).sp)
            Row(Modifier.padding(top = 10.dp * scale)) {
                TvAction(Modifier.height(72.dp * scale).testTag("backend-banner-dismiss")
                    .onFocusChanged { focus?.focused?.value = it.isFocused }, scale, requester = focus?.requester,
                    contentPadding = PaddingValues(horizontal = 34.dp * scale), activate = dismiss) {
                    Text("Dismiss", fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
