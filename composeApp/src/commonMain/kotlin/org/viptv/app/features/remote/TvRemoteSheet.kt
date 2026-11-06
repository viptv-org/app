package org.viptv.app.features.remote

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.*
import kotlinx.coroutines.CoroutineScope
import org.viptv.app.components.Button
import org.viptv.app.components.ClampedText
import org.viptv.app.components.IconButton
import org.viptv.app.components.Motion
import org.viptv.app.components.TextButton
import org.viptv.app.components.dismissScrim
import org.viptv.app.components.motionSpec
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.rememberOverlayMotion
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.VizioRemoteKey

internal val RemoteSurface3 = Color(0xFF2A2A2E)
internal val RemoteOnLight = Color(0xFF111113)
internal val RemoteScrim = Color.Black.copy(alpha = 0.62f)
private val RemoteHairline = Color.White.copy(alpha = .07f)

@Composable
internal fun TvRemoteOverlay(
    tv: VizioTv,
    receiverUrl: String,
    swipeMode: Boolean,
    onSwipeModeChange: (Boolean) -> Unit,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onPairAgain: () -> Unit = {},
    engineFactory: (() -> VizioRemoteEngine?)? = null,
) {
    val context = LocalPlatformContext.current
    val controller = remember(context, scope, tv, receiverUrl) {
        TvRemoteController(engineFactory ?: ({ vizioRemoteEngine(context, tv.origin) }), scope, receiverUrl)
    }
    DisposableEffect(controller) {
        controller.open()
        onDispose { controller.close() }
    }
    var visible by remember { mutableStateOf(true) }
    val leave: FiniteAnimationSpec<Float> = motionSpec(tween(Motion.leave, easing = Motion.exit))
    val motion = rememberOverlayMotion(visible,
        containerEnter = motionSpec(tween(Motion.panel, easing = Motion.standard)),
        scrimEnter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = leave)
    LaunchedEffect(motion.presented) { if (!motion.presented) onClose() }
    val dismiss = { visible = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        RemoteKeepScreenOn(motion.presented)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.scrim }.background(RemoteScrim).dismissScrim { dismiss() })
        RemoteSheet(tv, controller, motion.container, visible, swipeMode, onSwipeModeChange, dismiss, onPairAgain, maxWidth)
    }
}

@Composable
private fun BoxScope.RemoteSheet(
    tv: VizioTv,
    controller: TvRemoteController,
    container: Float,
    enabled: Boolean,
    swipeMode: Boolean,
    onSwipeModeChange: (Boolean) -> Unit,
    dismiss: () -> Unit,
    onPairAgain: () -> Unit,
    maxWidth: Dp,
) {
    val haptic = LocalHapticFeedback.current
    val send: (VizioRemoteKey) -> Unit = { key ->
        if (enabled && controller.key(key)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .graphicsLayer { translationY = size.height * (1f - container) }
        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
        .background(Palette.surface)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RemoteHeader(tv, controller, dismiss)
        if (controller.online || controller.checking) {
            Button({ controller.launchOnTv() }, enabled = controller.online && !controller.launching,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color(0xFF15130F))) {
                Text("Open VIPTV on TV", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            RemoteOfflineCard(tv.name, controller::retry, onPairAgain)
        }
        Box(Modifier.graphicsLayer { alpha = if (controller.online) 1f else 0.4f }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RemoteModeSwitch(swipeMode, onSwipeModeChange, enabled = controller.online)
                if (swipeMode) RemoteSwipePad(send, maxWidth) else RemotePad(send)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    RemoteBottomRow(send)
                }
            }
        }
    }
}

@Composable
private fun RemoteHeader(tv: VizioTv, controller: TvRemoteController, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.raised), contentAlignment = Alignment.Center) {
            Icon(Lucide.Tv, "TV", Modifier.size(24.dp), tint = Palette.text)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ClampedText(tv.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!controller.online && !controller.checking) Icon(Lucide.WifiOff, null, Modifier.size(13.dp), tint = Palette.secondary)
                ClampedText(
                    when {
                        controller.online -> "Connected · ${tv.origin.removePrefix("https://")}"
                        controller.checking -> tv.origin.removePrefix("https://")
                        else -> "Not reachable"
                    },
                    fontSize = 13.sp, color = Palette.secondary, maxLines = 1,
                )
            }
        }
        IconButton(onClick = close, modifier = Modifier.size(44.dp)) { Icon(Lucide.X, "Close remote", Modifier.size(22.dp), tint = Palette.text) }
    }
}

@Composable
private fun RemoteOfflineCard(tvName: String, retry: () -> Unit, pairAgain: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.raised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Can't reach $tvName", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Turn the TV on and check it's on the same Wi-Fi as this phone.", color = Palette.secondary, fontSize = 14.sp)
        TextButton(retry, contentPadding = PaddingValues(0.dp)) { Text("Try again", color = Palette.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(pairAgain, contentPadding = PaddingValues(0.dp)) { Text("Pair again", color = Palette.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun RemoteModeSwitch(swipe: Boolean, setSwipe: (Boolean) -> Unit, enabled: Boolean) {
    Row(Modifier.width(232.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(4.dp)) {
        RemoteSegment("Buttons", selected = !swipe, enabled) { setSwipe(false) }
        RemoteSegment("Swipe", selected = swipe, enabled) { setSwipe(true) }
    }
}

@Composable
private fun RowScope.RemoteSegment(label: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
        .background(if (selected) Palette.text else Color.Transparent)
        .pointerClickable(enabled = enabled, onClickLabel = label) { choose() }, contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) RemoteOnLight else Palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RemotePad(send: (VizioRemoteKey) -> Unit) {
    Box(Modifier.size(272.dp).clip(CircleShape).background(Palette.raised).border(1.dp, RemoteHairline, CircleShape)) {
        RemotePadKey(Lucide.ArrowUp, "Up", VizioRemoteKey.UP, send, Modifier.align(Alignment.TopCenter))
        RemotePadKey(Lucide.ArrowDown, "Down", VizioRemoteKey.DOWN, send, Modifier.align(Alignment.BottomCenter))
        RemotePadKey(Lucide.ArrowLeft, "Left", VizioRemoteKey.LEFT, send, Modifier.align(Alignment.CenterStart))
        RemotePadKey(Lucide.ArrowRight, "Right", VizioRemoteKey.RIGHT, send, Modifier.align(Alignment.CenterEnd))
        Box(Modifier.size(104.dp).align(Alignment.Center).clip(CircleShape).background(Palette.text)
            .pointerClickable(onClickLabel = "OK", shape = CircleShape, hover = null) { send(VizioRemoteKey.OK) },
            contentAlignment = Alignment.Center) {
            Text("OK", color = RemoteOnLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RemotePadKey(icon: ImageVector, label: String, key: VizioRemoteKey, send: (VizioRemoteKey) -> Unit, modifier: Modifier) {
    Box(modifier.size(88.dp).clip(CircleShape).pointerClickable(onClickLabel = label, shape = CircleShape, hover = null) { send(key) },
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(30.dp), tint = Palette.text)
    }
}

@Composable
private fun RemoteSwipePad(send: (VizioRemoteKey) -> Unit, maxWidth: Dp) {
    val density = LocalDensity.current
    val threshold = with(density) { remoteSwipeThresholdDp.dp.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.width(if (maxWidth - 32.dp < 358.dp) maxWidth - 32.dp else 358.dp).height(272.dp)
        .clip(RoundedCornerShape(28.dp)).background(Palette.raised, RoundedCornerShape(28.dp))
        .pointerClickable(onClickLabel = "OK", shape = RoundedCornerShape(28.dp), hover = null) { send(VizioRemoteKey.OK) }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { drag = Offset.Zero },
                onDrag = { change, amount -> change.consume(); drag += amount },
                onDragEnd = {
                    remoteSwipeKey(drag.x, drag.y, threshold)?.let(send)
                    drag = Offset.Zero
                },
            )
        }, contentAlignment = Alignment.Center) {
        Text("Swipe to move · Tap for OK", color = Palette.tertiary, fontSize = 14.sp)
    }
}

@Composable
private fun RemoteBottomRow(send: (VizioRemoteKey) -> Unit) {
    RemoteBottomKey(Lucide.Undo2, "Back", VizioRemoteKey.BACK, send)
    RemoteBottomKey(Lucide.Play, "Play", VizioRemoteKey.PLAY, send)
    RemoteBottomKey(Lucide.Pause, "Pause", VizioRemoteKey.PAUSE, send)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteRoundKey(Lucide.Minus, "Volume down", VizioRemoteKey.VOL_DOWN, send)
            RemoteRoundKey(Lucide.Plus, "Volume up", VizioRemoteKey.VOL_UP, send)
        }
        Text("Volume", color = Palette.tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RemoteBottomKey(icon: ImageVector, label: String, key: VizioRemoteKey, send: (VizioRemoteKey) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteRoundKey(icon, label, key, send)
        Text(label, color = Palette.tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RemoteRoundKey(icon: ImageVector, label: String, key: VizioRemoteKey, send: (VizioRemoteKey) -> Unit) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(RemoteSurface3)
        .pointerClickable(onClickLabel = label, shape = CircleShape, hover = null) { send(key) },
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(28.dp), tint = Palette.text)
    }
}