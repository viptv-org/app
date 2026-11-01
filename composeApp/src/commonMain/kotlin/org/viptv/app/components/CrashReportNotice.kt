package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.viptv.app.theme.Palette

@Composable
fun CrashReportNotice(television: Boolean, send: () -> Unit, discard: () -> Unit) {
    if (television) TvCrashNotice(send, discard) else PhoneCrashBanner(send, discard)
}

@Composable
private fun PhoneCrashBanner(send: () -> Unit, discard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp).padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("crash-report-banner"),
            RoundedCornerShape(16.dp), color = Palette.raised, contentColor = Palette.text, shadowElevation = 8.dp) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VIPTV closed unexpectedly. Send a crash report?", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(discard) { Text("No", color = Palette.secondary) }
                    TextButton(send) { Text("Send report", color = Palette.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun TvCrashNotice(send: () -> Unit, discard: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val first = remember { FocusRequester() }
        Popup(alignment = Alignment.TopEnd, onDismissRequest = discard, properties = PopupProperties(focusable = true)) {
            TvFocusColumn(Modifier.padding(top = 96.dp * scale, end = 96.dp * scale)) {
                Column(Modifier.width(760.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Color(0xFF202023))
                    .padding(horizontal = 44.dp * scale, vertical = 40.dp * scale).testTag("crash-report-prompt"),
                    verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    Text("VIPTV closed unexpectedly. Send a crash report?", color = Palette.text,
                        fontSize = (28 * scale).sp, lineHeight = (38 * scale).sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 8.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale, requester = first, activate = send) {
                            Text("Send report", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                        TvAction(Modifier.width(180.dp * scale).height(72.dp * scale), scale, activate = discard) {
                            Text("No", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { delay(700); first.requestFocus() }
        }
    }
}