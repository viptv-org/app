package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import com.composables.icons.lucide.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.player.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun PlayerScreen(input: PlaybackInput?, paused: Boolean, error: String?, toggle: () -> Unit,
    observe: (PlayerFact) -> Unit, close: () -> Unit, title: Card? = null, preview: PlayerFact.Progress? = null, phone: Boolean = false, previewFrame: String? = null, canvasColor: Color = Color.Black) {
    val commands = remember(input?.id) { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16) }
    var position by remember(input?.id) { mutableStateOf(input?.positionMillis ?: preview?.positionMillis ?: 0) }
    var duration by remember(input?.id) { mutableStateOf(preview?.durationMillis) }
    var tracks by remember(input?.id) { mutableStateOf(PlaybackTracks()) }
    var showTracks by remember { mutableStateOf(false) }
    var locked by remember(input?.id) { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(1f) }
    var subtitleSize by remember { mutableStateOf(1f) }
    var speed by remember { mutableStateOf(1.0) }
    var controlError by remember { mutableStateOf<String?>(null) }
    var buffering by remember(input?.id) { mutableStateOf(input != null) }
    LaunchedEffect(paused) { if (paused) visible = true }
    LaunchedEffect(visible, paused, interaction, buffering) {
        if (visible && !paused && !buffering && input != null) { delay(5000); visible = false }
    }
    fun send(command: PlayerCommand) { commands.tryEmit(command); interaction++; visible = true; controlError = null }
    fun seek(value: Long) { send(PlayerCommand.Seek(value.coerceIn(0, duration ?: Long.MAX_VALUE))) }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(canvasColor, blendMode = BlendMode.Src) }) {
        if (input == null && previewFrame != null) AsyncImage(previewFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (input != null) PlayerSurface(input, paused, Modifier.fillMaxSize(), { fact ->
            when (fact) {
                is PlayerFact.Properties -> { volume = fact.volume; speed = fact.speed; subtitleSize = fact.subtitleSize }
                is PlayerFact.Tracks -> tracks = fact.options
                is PlayerFact.Progress -> { position = fact.positionMillis; duration = fact.durationMillis }
                is PlayerFact.Status -> buffering = fact.name in listOf("Opening", "Buffering", "Preparing")
                PlayerFact.ControlFailed -> controlError = "Could not apply this playback control."
                PlayerFact.Failed -> buffering = false
            }
            observe(fact)
        }, commands)
        PlayerOverlayHost(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.Escape -> { close(); true }
            Key.Spacebar, Key.MediaPlayPause -> { visible = true; interaction++; toggle(); true }
            else -> if (!visible && !locked) { visible = true; interaction++; true } else false
        }
    }) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !locked) { visible = !visible; interaction++ })
        if (locked) FilledIconButton(onClick = { locked = false; visible = true; interaction++ },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp).size(56.dp)) {
            Icon(Lucide.LockKeyholeOpen, "Unlock controls", Modifier.size(24.dp))
        }
        if (visible && !locked && !(phone && showTracks)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f),
                0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Row(Modifier.align(Alignment.TopStart).padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = close) { Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp)) }
                Column {
                    Text(title?.title ?: "Playback", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    title?.subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.secondary) }
                }
            }
            if (phone) Box(Modifier.align(Alignment.Center)) {
                PhoneTransport(paused, { interaction++; toggle() }, { seek(position + it) })
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                PlaybackTimeline(position, duration, ::seek, remaining = phone)
                if (phone) PhonePlaybackToolbar(speed, { speed = it; send(PlayerCommand.Speed(it)) }, { locked = true }, { showTracks = true })
                else PlaybackButtons(paused, { interaction++; toggle() }, { seek(position + it) }, volume,
                    { volume = it; send(PlayerCommand.Volume(it)) }, speed, { speed = it; send(PlayerCommand.Speed(it)) }, close, { showTracks = true })
            }
        }
        if (showTracks) PlaybackTrackPicker(tracks, { send(it) }, { showTracks = false }, phone, subtitleSize, { send(PlayerCommand.SubtitleSize(it)) })
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Palette.text)
        (error ?: controlError)?.let { Text(it, Modifier.align(Alignment.Center).padding(24.dp), color = Palette.danger) }
        }
    }
}
