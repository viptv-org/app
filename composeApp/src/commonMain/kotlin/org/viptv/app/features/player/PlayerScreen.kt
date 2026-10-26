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
import androidx.compose.ui.focus.FocusRequester
import org.viptv.app.connection.cardLookupKey
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
    observe: (PlayerFact) -> Unit, close: () -> Unit, title: Card? = null, preview: PlayerFact.Progress? = null, phone: Boolean = false, previewFrame: String? = null, canvasColor: Color = Color.Black,
    television: Boolean = false, tvExtras: TvPlaybackExtras = TvPlaybackExtras(), chooseSources: (() -> Unit)? = null,
    playEpisode: ((Card) -> Unit)? = null, retry: (() -> Unit)? = null, backChanged: ((() -> Unit)?) -> Unit = {}, playbackKey: Any? = input,
    nextEpisodeLoading: Boolean = false, trackChosen: (PlaybackTrackKind, String?) -> Unit = { _, _ -> },
    /** The video and its scrims draw edge to edge, under a display cutout; tappable controls stay inside these insets. */
    controlInsets: WindowInsets = WindowInsets.safeDrawing) {
    if (television) {
        TvPlayerScreen(input, paused, error, toggle, observe, close, title, tvExtras, chooseSources, playEpisode, retry, backChanged,
            previewFrame = previewFrame, initialFacts = listOfNotNull(preview), playbackKey = playbackKey, trackChosen = trackChosen)
        return
    }
    val commands = remember(playbackKey) { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 16) }
    var position by remember(playbackKey) { mutableStateOf(input?.let { it.positionMillis + it.timelineOffsetMillis } ?: preview?.positionMillis ?: 0) }
    var duration by remember(playbackKey) { mutableStateOf(input?.durationMillis ?: preview?.durationMillis) }
    var tracks by remember(playbackKey) { mutableStateOf(PlaybackTracks()) }
    var seekTarget by remember(playbackKey) { mutableStateOf<Long?>(null) }
    var seekMark by remember(playbackKey) { mutableStateOf(kotlin.time.TimeSource.Monotonic.markNow()) }
    var retainedInput by remember(playbackKey) { mutableStateOf(input) }
    SideEffect { if (input != null) retainedInput = input }
    val rendering = input ?: retainedInput
    var showTracks by remember(if (phone) playbackKey else Unit) { mutableStateOf(false) }
    var locked by remember(playbackKey) { mutableStateOf(false) }
    var visible by remember(if (phone) playbackKey else Unit) { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(1f) }
    var subtitleSize by remember { mutableStateOf(1f) }
    var speed by remember { mutableStateOf(1.0) }
    var controlError by remember { mutableStateOf<String?>(null) }
    var buffering by remember(playbackKey) { mutableStateOf(input != null) }
    var failed by remember(playbackKey, input) { mutableStateOf(false) }
    var phonePanel by remember(playbackKey) { mutableStateOf<PhonePlaybackPanel?>(null) }
    var nextChoice by remember(playbackKey) { mutableStateOf<Card?>(null) }
    var restorePanel by remember(playbackKey) { mutableStateOf<PhonePlaybackPanel?>(null) }
    val episodesFocus = remember { FocusRequester() }; val nextFocus = remember { FocusRequester() }
    val episodes = if (phone && input?.live != true) phonePlaybackEpisodes(title, tvExtras.episodes) else emptyList()
    val next = tvExtras.nextEpisode.takeIf { input?.live != true }
    val episodeTitle = phone && input?.live != true && title?.identity?.episode != null
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestNext by rememberUpdatedState(next)
    val latestPlayEpisode by rememberUpdatedState(playEpisode)
    val latestPlaybackKey by rememberUpdatedState(playbackKey)
    val latestTitle by rememberUpdatedState(title)
    val latestClose by rememberUpdatedState(close)
    fun dismissPanel() { restorePanel = phonePanel; phonePanel = null; visible = true; interaction++ }
    val phoneBack = {
        when { showTracks -> { showTracks = false; visible = true }
            phonePanel != null -> dismissPanel()
            locked -> { locked = false; visible = true }
            else -> latestClose() }
    }
    val latestPhoneBack by rememberUpdatedState(phoneBack)
    DisposableEffect(phone, playbackKey) {
        if (phone) backChanged { latestPhoneBack() }
        onDispose { if (phone) backChanged(null) }
    }
    LaunchedEffect(restorePanel, phonePanel) {
        val restoring = restorePanel
        if (phone && restoring != null && phonePanel == null) {
            withFrameNanos { }
            if (restoring == PhonePlaybackPanel.Episodes && episodes.isNotEmpty() && playEpisode != null) episodesFocus.requestFocus()
            else if (restoring == PhonePlaybackPanel.Next && next != null && playEpisode != null) nextFocus.requestFocus()
            restorePanel = null
        }
    }
    LaunchedEffect(phonePanel, next, playEpisode, episodes.isEmpty()) {
        if (phone && phonePanel != null && (playEpisode == null ||
                phonePanel == PhonePlaybackPanel.Next && (next == null || nextChoice?.let(::cardLookupKey) != cardLookupKey(next)) ||
                phonePanel == PhonePlaybackPanel.Episodes && episodes.isEmpty())) dismissPanel()
    }
    LaunchedEffect(paused) { if (paused) visible = true }
    LaunchedEffect(visible, paused, interaction, buffering, phonePanel, showTracks, failed) {
        if (visible && !paused && !buffering && phonePanel == null && !showTracks && !failed && input != null) { delay(5000); visible = false }
    }
    fun send(command: PlayerCommand) { commands.tryEmit(command); interaction++; visible = true; controlError = null }
    fun seek(value: Long) { if (input?.live != true) {
        val target = value.coerceIn(0, duration ?: Long.MAX_VALUE)
        seekTarget = target; position = target; seekMark = kotlin.time.TimeSource.Monotonic.markNow()
        send(PlayerCommand.Seek(target))
    } }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(canvasColor, blendMode = BlendMode.Src) }) {
        if (input == null && previewFrame != null) AsyncImage(previewFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (rendering != null) PlayerSurface(rendering, paused || input == null, Modifier.fillMaxSize(), { fact ->
            if (input === rendering) {
            when (fact) {
                is PlayerFact.Properties -> { volume = fact.volume; speed = fact.speed; subtitleSize = fact.subtitleSize }
                is PlayerFact.Tracks -> tracks = fact.options
                is PlayerFact.Progress -> {
                    duration = fact.durationMillis ?: duration
                    if (seekTarget == null || kotlin.math.abs(fact.positionMillis - seekTarget!!) <= 1500 || seekMark.elapsedNow().inWholeSeconds >= 10) {
                        position = fact.positionMillis; seekTarget = null
                    }
                }
                is PlayerFact.Status -> buffering = !failed && fact.name in listOf("Opening", "Buffering", "Preparing")
            is PlayerFact.Presentation -> Unit
                PlayerFact.ControlFailed -> controlError = "Could not apply this playback control."
                PlayerFact.Failed -> { buffering = false; failed = true }
            }
            observe(fact)
            }
        }, commands)
        PlayerOverlayHost(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.Escape -> { if (phone) latestPhoneBack() else close(); true }
            Key.Spacebar, Key.MediaPlayPause -> { if (!phone || !failed && error == null) { visible = true; interaction++; toggle() }; true }
            else -> if (!visible && !locked) { visible = true; interaction++; true } else false
        }
    }) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !locked) { visible = !visible; interaction++ })
        if (locked) FilledIconButton(onClick = { locked = false; visible = true; interaction++ },
            modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(controlInsets.only(WindowInsetsSides.Horizontal)).padding(start = 32.dp).size(56.dp)) {
            Icon(Lucide.LockKeyholeOpen, "Unlock controls", Modifier.size(24.dp))
        }
        if (visible && !locked && !(phone && (showTracks || phonePanel != null || failed || error != null))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f),
                0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Row(Modifier.align(Alignment.TopStart).windowInsetsPadding(controlInsets).padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = close) { Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp)) }
                Column {
                    Text(title?.title ?: "Playback", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    title?.subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.secondary) }
                }
            }
            if (phone) Box(Modifier.align(Alignment.Center).windowInsetsPadding(controlInsets.only(WindowInsetsSides.Horizontal))) {
                PhoneTransport(paused, { interaction++; toggle() }, { seek(position + it) }, seekEnabled = input?.live != true)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(controlInsets).padding(horizontal = 24.dp, vertical = 12.dp)) {
                if (input?.live == true || title?.identity?.kind in listOf("channel", "tv") && duration == null) {
                    Text("LIVE", color = Palette.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else PlaybackTimeline(position, duration, ::seek, remaining = phone)
                if (phone) {
                    if (episodeTitle && next == null) Text(if (nextEpisodeLoading) "Finding next episode…" else "Next episode unavailable",
                        color = Palette.secondary, fontSize = 12.sp)
                    PhonePlaybackToolbar(speed, { speed = it; send(PlayerCommand.Speed(it)) }, { locked = true }, { showTracks = true },
                        episodes = if (episodeTitle) ({ phonePanel = PhonePlaybackPanel.Episodes; interaction++ }) else null,
                        episodesEnabled = episodes.isNotEmpty() && playEpisode != null,
                        next = if (episodeTitle) ({ nextChoice = next; phonePanel = PhonePlaybackPanel.Next; interaction++ }) else null,
                        nextEnabled = next != null && playEpisode != null, episodesFocus = episodesFocus, nextFocus = nextFocus)
                }
                else PlaybackButtons(paused, { interaction++; toggle() }, { seek(position + it) }, volume,
                    { volume = it; send(PlayerCommand.Volume(it)) }, speed, { speed = it; send(PlayerCommand.Speed(it)) }, close, { showTracks = true }, seekEnabled = input?.live != true,
                    streams = if (input?.live != true) chooseSources else null, streamLabel = tvExtras.sourceLabel)
            }
        }
        if (showTracks && !(phone && (failed || error != null))) PlaybackTrackPicker(tracks, { choice ->
            send(choice)
            trackChosen(choice.kind, (if (choice.kind == PlaybackTrackKind.Audio) tracks.audio else tracks.subtitles)
                .firstOrNull { it.id == choice.id }?.language)
        }, { showTracks = false }, phone, subtitleSize, { send(PlayerCommand.SubtitleSize(it)) }, controlInsets)
        if (phone && phonePanel == PhonePlaybackPanel.Episodes) PhoneEpisodePicker(title, episodes, { selected ->
            if (latestPlaybackKey == playbackKey && latestTitle?.let(::cardLookupKey) == title?.let(::cardLookupKey)) {
                latestEpisodes.find { cardLookupKey(it) == cardLookupKey(selected) }?.let { phonePanel = null; latestPlayEpisode?.invoke(it) }
            }
        }, ::dismissPanel, controlInsets)
        if (phone && phonePanel == PhonePlaybackPanel.Next) nextChoice?.let { candidate -> PhoneNextEpisode(candidate, {
            if (latestPlaybackKey == playbackKey && latestTitle?.let(::cardLookupKey) == title?.let(::cardLookupKey) &&
                latestNext?.let(::cardLookupKey) == cardLookupKey(candidate)) {
                phonePanel = null; latestNext?.let { latestPlayEpisode?.invoke(it) }
            }
        }, ::dismissPanel, controlInsets) }
        if (phone && (failed || error != null)) PhonePlayerFailure(tvExtras.failureDescription ?: error ?:
            "Choose another source to continue from ${playbackTime(position)}.", tvExtras.fallbackLabel, retry, chooseSources, close, title, controlInsets)
        else {
            if (buffering && !failed && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Palette.text)
            (error ?: controlError)?.let { Text(it, Modifier.align(Alignment.Center).windowInsetsPadding(controlInsets.only(WindowInsetsSides.Horizontal)).padding(24.dp), color = Palette.danger) }
        }
        }
    }
}

private enum class PhonePlaybackPanel { Episodes, Next }
