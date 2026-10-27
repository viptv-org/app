package org.viptv.app.features.player

import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.viptv.app.player.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.IconButton

@Composable
fun PlayerScreen(input: PlaybackInput?, paused: Boolean, error: String?, toggle: () -> Unit,
    observe: (PlayerFact) -> Unit, close: () -> Unit, title: Card? = null, preview: PlayerFact.Progress? = null, phone: Boolean = false, previewFrame: String? = null, canvasColor: Color = Color.Black,
    television: Boolean = false, tvExtras: TvPlaybackExtras = TvPlaybackExtras(), chooseSources: (() -> Unit)? = null,
    playEpisode: ((Card) -> Unit)? = null, retry: (() -> Unit)? = null, backChanged: ((() -> Unit)?) -> Unit = {}, playbackKey: Any? = input,
    nextEpisodeLoading: Boolean = false, trackChosen: (PlaybackTrackKind, String?) -> Unit = { _, _ -> },
    /** The video and its scrims draw edge to edge, under a display cutout; tappable controls stay inside these insets. */
    controlInsets: WindowInsets = WindowInsets.safeDrawing,
    /** Facts from a preview or test harness rather than a mounted surface; never forwarded to [observe]. */
    externalFacts: Flow<PlayerFact> = emptyFlow(),
    /** Opens the full Subtitles & audio settings from the Audio & subtitles menu (app#44). */
    openSettings: (() -> Unit)? = null) {
    if (television) {
        TvPlayerScreen(input, paused, error, toggle, observe, close, title, tvExtras, chooseSources, playEpisode, retry, backChanged,
            previewFrame = previewFrame, initialFacts = listOfNotNull(preview), externalFacts = externalFacts, playbackKey = playbackKey,
            trackChosen = trackChosen, canvasColor = canvasColor, openSettings = openSettings)
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
    // The player reports the end of the media; the next-episode panel opens once per end.
    var ended by remember(playbackKey) { mutableStateOf(false) }
    var endHandled by remember(playbackKey) { mutableStateOf(false) }
    val episodesFocus = remember { FocusRequester() }; val nextFocus = remember { FocusRequester() }
    val episodes = if (phone && input?.live != true) phonePlaybackEpisodes(title, tvExtras.episodes) else emptyList()
    val next = tvExtras.nextEpisode.takeIf { input?.live != true }
    val seriesEpisode = input?.live != true && title?.identity?.episode != null
    val episodeTitle = phone && seriesEpisode
    // Next during playback goes straight to the next episode; the panel belongs to the end.
    val playNext = { next?.let { playEpisode?.invoke(it) }; Unit }
    val nextEnabled = next != null && playEpisode != null
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestNext by rememberUpdatedState(next)
    val latestPlayEpisode by rememberUpdatedState(playEpisode)
    val latestPlaybackKey by rememberUpdatedState(playbackKey)
    val latestTitle by rememberUpdatedState(title)
    val latestClose by rememberUpdatedState(close)
    fun closePanel() { restorePanel = phonePanel; phonePanel = null; visible = true; interaction++ }
    /** The viewer closed the panel: an end panel stays closed until the media ends again. */
    fun dismissPanel() { if (phonePanel == PhonePlaybackPanel.Next) endHandled = true; closePanel() }
    val phoneBack = {
        when { showTracks -> { showTracks = false; visible = true }
            phonePanel != null -> dismissPanel()
            locked -> { locked = false; visible = true }
            else -> latestClose() }
    }
    val latestPhoneBack by rememberUpdatedState(phoneBack)
    // Desktop and browser Back (mouse button, browser history) take Escape's step: an open panel closes first.
    val keyboardBack = { if (phonePanel != null) dismissPanel() else latestClose() }
    val latestKeyboardBack by rememberUpdatedState(keyboardBack)
    DisposableEffect(phone, playbackKey) {
        backChanged { if (phone) latestPhoneBack() else latestKeyboardBack() }
        onDispose { backChanged(null) }
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
        if (phonePanel != null && (playEpisode == null ||
                phonePanel == PhonePlaybackPanel.Next && (next == null || nextChoice?.let(::cardLookupKey) != cardLookupKey(next)) ||
                phonePanel == PhonePlaybackPanel.Episodes && episodes.isEmpty())) closePanel()
    }
    LaunchedEffect(ended, endHandled, next, playEpisode != null, phonePanel, failed, error) {
        if (ended && !endHandled && seriesEpisode && next != null && playEpisode != null && phonePanel == null && !failed && error == null) {
            showTracks = false; locked = false
            nextChoice = next; phonePanel = PhonePlaybackPanel.Next
        }
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
    /** Mirror a player fact into this screen's state. */
    fun applyFact(fact: PlayerFact) {
        when (fact) {
            is PlayerFact.Properties -> { volume = fact.volume; speed = fact.speed; subtitleSize = fact.subtitleSize }
            is PlayerFact.Tracks -> tracks = fact.options
            is PlayerFact.Progress -> {
                duration = fact.durationMillis ?: duration
                if (seekTarget == null || kotlin.math.abs(fact.positionMillis - seekTarget!!) <= 1500 || seekMark.elapsedNow().inWholeSeconds >= 10) {
                    position = fact.positionMillis; seekTarget = null
                }
            }
            is PlayerFact.Status -> {
                buffering = !failed && fact.name in listOf("Opening", "Buffering", "Preparing")
                ended = fact.name == "Ended"
                if (!ended) endHandled = false
            }
            is PlayerFact.Presentation -> Unit
            PlayerFact.ControlFailed -> controlError = "Could not apply this playback control."
            PlayerFact.Failed -> { buffering = false; failed = true }
        }
    }
    LaunchedEffect(externalFacts, playbackKey) { externalFacts.collect(::applyFact) }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(canvasColor, blendMode = BlendMode.Src) }) {
        if (input == null && previewFrame != null) AsyncImage(previewFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (rendering != null) PlayerSurface(rendering, paused || input == null, Modifier.fillMaxSize(), { fact ->
            if (input === rendering) { applyFact(fact); observe(fact) }
        }, commands)
        PlayerOverlayHost(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.Escape -> { if (phone) latestPhoneBack() else if (phonePanel != null) dismissPanel() else close(); true }
            Key.Spacebar, Key.MediaPlayPause -> { if (!phone || !failed && error == null) { visible = true; interaction++; toggle() }; true }
            else -> if (!visible && !locked) { visible = true; interaction++; true } else false
        }
    }) {
        Box(Modifier.fillMaxSize().pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !locked) { visible = !visible; interaction++ })
        if (locked) FilledIconButton(onClick = { locked = false; visible = true; interaction++ },
            modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(controlInsets.only(WindowInsetsSides.Horizontal)).padding(start = 32.dp).size(56.dp)) {
            Icon(Lucide.LockKeyholeOpen, "Unlock controls", Modifier.size(24.dp))
        }
        if (visible && !locked && phonePanel == null && !(phone && (showTracks || failed || error != null))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f),
                0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Row(Modifier.align(Alignment.TopStart).windowInsetsPadding(controlInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(24.dp), verticalAlignment = Alignment.CenterVertically,
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
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(controlInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                if (input?.live == true || title?.identity?.kind in listOf("channel", "tv") && duration == null) {
                    Text("LIVE", color = Palette.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else PlaybackTimeline(position, duration, ::seek, remaining = phone)
                if (phone) {
                    if (episodeTitle && next == null) Text(if (nextEpisodeLoading) "Finding next episode…" else "Next episode unavailable",
                        color = Palette.secondary, fontSize = 12.sp)
                    PhonePlaybackToolbar(speed, { speed = it; send(PlayerCommand.Speed(it)) }, { locked = true }, { showTracks = true },
                        episodes = if (episodeTitle) ({ phonePanel = PhonePlaybackPanel.Episodes; interaction++ }) else null,
                        episodesEnabled = episodes.isNotEmpty() && playEpisode != null,
                        next = if (episodeTitle) playNext else null,
                        nextEnabled = nextEnabled, episodesFocus = episodesFocus, nextFocus = nextFocus)
                }
                else PlaybackButtons(paused, { interaction++; toggle() }, { seek(position + it) }, volume,
                    { volume = it; send(PlayerCommand.Volume(it)) }, speed, { speed = it; send(PlayerCommand.Speed(it)) }, close, { showTracks = true }, seekEnabled = input?.live != true,
                    streams = if (input?.live != true) chooseSources else null, streamLabel = tvExtras.sourceLabel,
                    next = if (seriesEpisode) playNext else null, nextEnabled = nextEnabled)
            }
        }
        if (showTracks && !(phone && (failed || error != null))) PlaybackTrackPicker(tracks, { choice ->
            send(choice)
            trackChosen(choice.kind, (if (choice.kind == PlaybackTrackKind.Audio) tracks.audio else tracks.subtitles)
                .firstOrNull { it.id == choice.id }?.language)
        }, { showTracks = false }, phone, subtitleSize, { send(PlayerCommand.SubtitleSize(it)) }, controlInsets, openSettings)
        if (phone && phonePanel == PhonePlaybackPanel.Episodes) PhoneEpisodePicker(title, episodes, { selected ->
            if (latestPlaybackKey == playbackKey && latestTitle?.let(::cardLookupKey) == title?.let(::cardLookupKey)) {
                latestEpisodes.find { cardLookupKey(it) == cardLookupKey(selected) }?.let { phonePanel = null; latestPlayEpisode?.invoke(it) }
            }
        }, ::dismissPanel, controlInsets)
        if (phonePanel == PhonePlaybackPanel.Next) nextChoice?.let { candidate -> PhoneNextEpisode(candidate, {
            if (latestPlaybackKey == playbackKey && latestTitle?.let(::cardLookupKey) == title?.let(::cardLookupKey) &&
                latestNext?.let(::cardLookupKey) == cardLookupKey(candidate)) {
                endHandled = true; phonePanel = null; latestNext?.let { latestPlayEpisode?.invoke(it) }
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

/** A side sheet's surface reaches the right screen edge; its content clears a cutout or bar there. */
internal fun WindowInsets.endSheet(): WindowInsets = only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
