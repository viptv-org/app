package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.Cast
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.*
import org.viptv.app.components.AppBasicTextField
import org.viptv.app.components.AppTextField
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.components.pointerClickable
import org.viptv.app.features.entry.DeviceCheckTile
import org.viptv.app.features.entry.DeviceError
import org.viptv.app.features.entry.DevicePrimaryButton
import org.viptv.app.features.entry.DeviceSecondaryButton
import org.viptv.app.features.entry.DeviceSubtitle
import org.viptv.app.features.entry.DeviceTile
import org.viptv.app.features.entry.DeviceTitle
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.VizioPairingChallenge

data class VizioTv(val name: String, val origin: String)

internal sealed interface VizioSetupStep {
    data object Intro : VizioSetupStep
    data object Searching : VizioSetupStep
    data object Manual : VizioSetupStep
    data object NoAccess : VizioSetupStep
    data class Pin(val tv: VizioTv) : VizioSetupStep
    data class Done(val tv: VizioTv) : VizioSetupStep
}

internal sealed interface VizioPairResult {
    data object Paired : VizioPairResult
    data object Rejected : VizioPairResult
    data object Failed : VizioPairResult
}

internal interface VizioSetupEngine {
    suspend fun search(limit: Int, onResult: (VizioTv) -> Unit)
    suspend fun probe(origin: String): VizioTv?
    suspend fun beginPair(tv: VizioTv): VizioPairingChallenge?
    suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult
    suspend fun cancelPair(tv: VizioTv)
    fun openLocalNetworkSettings() {}
}

internal expect fun vizioSetupEngine(context: PlatformContext): VizioSetupEngine?

@Composable
internal fun rememberVizioSetupEngine(): VizioSetupEngine? {
    val context = LocalPlatformContext.current
    return remember(context) { vizioSetupEngine(context) }
}

internal const val vizioSearchLimit = 16
private const val vizioSearchTimeoutMillis = 30_000L

internal class VizioNoLocalNetwork : Exception()
internal class VizioNoAccess : Exception()

// A private local IPv4 address with an optional SmartCast port, canonicalized to https.
internal fun vizioManualOrigin(value: String): String? {
    val trimmed = value.trim().removePrefix("https://").removePrefix("http://")
    val parts = trimmed.split(':')
    if (parts.size !in 1..2) return null
    val octets = parts[0].split('.')
    if (octets.size != 4 || octets.any { it.toIntOrNull()?.let { v -> v in 0..255 } != true }) return null
    val a = octets[0].toInt(); val b = octets[1].toInt()
    if (!(a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254))) return null
    val port = if (parts.size == 1) 7345 else parts[1].toIntOrNull() ?: return null
    if (port !in setOf(7345, 9000)) return null
    return "https://${octets.joinToString(".")}:$port"
}

@Stable
internal class WatchOnTvSetupController(
    private val engine: VizioSetupEngine,
    private val scope: CoroutineScope,
    private val paired: (VizioTv) -> Unit,
) {
    var step by mutableStateOf<VizioSetupStep>(VizioSetupStep.Intro)
        private set
    var searching by mutableStateOf(false); private set
    var results by mutableStateOf<List<VizioTv>>(emptyList()); private set
    var searchError by mutableStateOf<String?>(null); private set
    var manual by mutableStateOf(""); private set
    var manualError by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var pin by mutableStateOf(""); private set
    var pinError by mutableStateOf<String?>(null); private set
    var pinReady by mutableStateOf(false); private set

    private var searchJob: Job? = null
    private var probeJob: Job? = null
    private var pairJob: Job? = null
    private var challenge: VizioPairingChallenge? = null
    private var pendingPair: VizioTv? = null
    private var pinReturnToSearch = true
    private var pinCancel: (() -> Unit)? = null
    private var noAccessRetry: () -> Unit = ::beginSearch

    fun beginSearch() {
        if (searchJob != null) return
        pinReturnToSearch = true
        step = VizioSetupStep.Searching
        results = emptyList(); searchError = null
        searching = true
        searchJob = scope.launch {
            try {
                withTimeout(vizioSearchTimeoutMillis) { engine.search(vizioSearchLimit) { results = results + it } }
            } catch (_: TimeoutCancellationException) {
                // Partial results are already published.
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: VizioNoAccess) { noAccessRetry = ::beginSearch; step = VizioSetupStep.NoAccess }
            catch (_: VizioNoLocalNetwork) { searchError = "Connect this phone to the same Wi-Fi as your TV." }
            catch (_: Exception) { searchError = "Could not search for TVs. Enter the IP address instead." }
            finally { searching = false; searchJob = null }
        }
    }

    fun openManual() {
        stopSearch()
        step = VizioSetupStep.Manual
        manualError = null
    }

    fun manualChanged(value: String) {
        manual = value
        manualError = null
    }

    fun connectManual() {
        val origin = vizioManualOrigin(manual)
        if (origin == null) {
            manualError = "Enter a local IPv4 address, optionally followed by :7345 or :9000."
            return
        }
        if (busy) return
        manualError = null
        pinReturnToSearch = false
        busy = true
        probeJob?.cancel()
        probeJob = scope.launch {
            try {
                val tv = engine.probe(origin)
                if (tv == null) manualError = "Can't reach that TV. Check the address is on the same Wi-Fi and try again."
                else beginPin(tv)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: VizioNoAccess) { busy = false; noAccessRetry = ::connectManual; step = VizioSetupStep.NoAccess }
            catch (_: Exception) { manualError = "Can't reach that TV. Check the address is on the same Wi-Fi and try again." }
            finally {
                probeJob = null
                if (step is VizioSetupStep.Manual) busy = false
            }
        }
    }

    fun cancelManual() {
        probeJob?.cancel(); probeJob = null
        busy = false; manualError = null
        step = VizioSetupStep.Searching
    }

    fun choose(tv: VizioTv) {
        stopSearch()
        pinReturnToSearch = true
        beginPin(tv)
    }

    fun newPin() {
        val tv = (step as? VizioSetupStep.Pin)?.tv ?: return
        beginPair(tv)
    }

    fun pairAgain(tv: VizioTv, cancel: () -> Unit) {
        stopSearch()
        pinReturnToSearch = false
        beginPin(tv)
        pinCancel = cancel
    }

    fun openLocalNetworkSettings() = engine.openLocalNetworkSettings()

    fun retry() = noAccessRetry()

    fun cancelPin() {
        val tv = (step as? VizioSetupStep.Pin)?.tv ?: return
        cancelPairing(tv)
        val leave = pinCancel
        pinCancel = null
        if (leave != null) leave() else step = if (pinReturnToSearch) VizioSetupStep.Searching else VizioSetupStep.Manual
    }

    fun pinChanged(value: String) {
        if (busy) return
        pin = value.filter(Char::isDigit).take(4)
        pinError = null
        if (pin.length == 4) submitPin()
    }

    private fun beginPin(tv: VizioTv) {
        step = VizioSetupStep.Pin(tv)
        pinCancel = null
        beginPair(tv)
    }

    private fun beginPair(tv: VizioTv) {
        pairJob?.cancel()
        pin = ""; pinError = null; challenge = null; pinReady = false
        busy = true
        pairJob = scope.launch {
            try {
                pendingPair?.let { cancelPairBestEffort(it) }
                pendingPair = tv
                val got = engine.beginPair(tv)
                if (got == null) {
                    pendingPair = null
                    pinError = "Can't reach ${tv.name}. Check the TV is on the same Wi-Fi and try again."
                } else {
                    challenge = got
                    pinReady = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                pendingPair = null
                pinError = "Can't reach ${tv.name}. Check the TV is on the same Wi-Fi and try again."
            } finally { pairJob = null; busy = false }
        }
    }

    private fun submitPin() {
        val tv = (step as? VizioSetupStep.Pin)?.tv ?: return
        val challenge = challenge ?: return
        if (busy) return
        val entered = pin
        pin = ""
        busy = true
        pairJob?.cancel()
        pairJob = scope.launch {
            try {
                when (val result = engine.finishPair(tv, challenge, entered)) {
                    VizioPairResult.Paired -> {
                        pendingPair = null
                        pinCancel = null
                        paired(tv)
                        step = VizioSetupStep.Done(tv)
                    }
                    VizioPairResult.Rejected -> pinError = "Incorrect PIN. Try again."
                    VizioPairResult.Failed -> pinError = "Can't reach ${tv.name}. Check the TV is on the same Wi-Fi and try again."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                pinError = "Can't reach ${tv.name}. Check the TV is on the same Wi-Fi and try again."
            } finally { pairJob = null; busy = false }
        }
    }

    private fun cancelPairing(tv: VizioTv) {
        pairJob?.cancel(); pairJob = null
        busy = false; challenge = null; pinReady = false; pin = ""; pinError = null
        if (pendingPair != null) {
            pendingPair = null
            scope.launch { cancelPairBestEffort(tv) }
        }
    }

    private fun stopSearch() {
        searchJob?.cancel(); searchJob = null
        searching = false
    }

    private suspend fun cancelPairBestEffort(tv: VizioTv) {
        try { engine.cancelPair(tv) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
}

@Composable
internal fun WatchOnTvSetupFlow(
    engine: VizioSetupEngine,
    compact: Boolean,
    back: () -> Unit,
    onPaired: (VizioTv) -> Unit,
    pairAgain: VizioTv? = null,
    onCancel: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(engine) { WatchOnTvSetupController(engine, scope, onPaired) }
    LaunchedEffect(engine, pairAgain) {
        val tv = pairAgain
        if (tv != null) controller.pairAgain(tv) { onCancel?.invoke() }
    }
    val step = controller.step
    TvFocusScroll(contentPadding = PaddingValues(vertical = if (compact) 16.dp else 32.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 32.dp).pageBottomClearance(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                VizioSetupStep.Intro -> VizioSetupIntro(compact, controller::beginSearch)
                VizioSetupStep.Searching -> VizioSetupSearch(compact, controller)
                VizioSetupStep.Manual -> VizioSetupManual(compact, controller)
                VizioSetupStep.NoAccess -> VizioSetupNoAccess(compact, controller)
                is VizioSetupStep.Pin -> VizioSetupPin(compact, step.tv, controller)
                is VizioSetupStep.Done -> VizioSetupDone(compact, step.tv, back)
            }
        }
    }
}

@Composable
private fun VizioSetupIntro(compact: Boolean, next: () -> Unit) {
    DeviceTile()
    DeviceTitle("Use this phone as your TV remote", compact)
    DeviceSubtitle("Works with Vizio SmartCast TVs on the same Wi-Fi as this phone. It's off until you set it up.", compact)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VizioPoint("Arrows, OK, Back, play and volume for your TV.")
        VizioPoint("Opens VIPTV on the TV in one tap.")
        VizioPoint("What's playing on this phone stays here. Nothing is sent to the TV.")
    }
    Spacer(Modifier.height(8.dp))
    Text("VIPTV searches your local network only to find your TV.", color = Palette.tertiary, fontSize = 13.sp, textAlign = TextAlign.Center)
    DevicePrimaryButton("Set up a TV", true, false, next)
}

@Composable
private fun VizioPoint(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = Palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Palette.body, fontSize = 14.sp)
    }
}

@Composable
private fun VizioSetupSearch(compact: Boolean, controller: WatchOnTvSetupController) {
    DeviceTile()
    DeviceTitle("Choose your TV", compact)
    when {
        controller.searching && controller.results.isEmpty() -> {
            CircularProgressIndicator(Modifier.size(28.dp), color = Palette.secondary, strokeWidth = 3.dp)
            DeviceSubtitle("Looking for Vizio TVs on your Wi-Fi…", compact)
        }
        controller.results.isNotEmpty() -> {
            SurfaceList(controller.results) { controller.choose(it) }
            if (controller.searching) LinearProgressIndicator(
                Modifier.fillMaxWidth().height(3.dp), color = Palette.accent, trackColor = Palette.raised)
        }
        else -> Unit
    }
    if (controller.results.isEmpty() && !controller.searching) {
        DeviceSubtitle("Don't see your TV?", compact)
        DeviceSubtitle("Turn the TV on, then check it's on the same Wi-Fi as this phone. It can take a few seconds to show up.", compact)
    }
    controller.searchError?.let { DeviceError(it) }
    Spacer(Modifier.height(8.dp))
    DeviceSecondaryButton("Enter IP address", controller::openManual)
}

@Composable
private fun SurfaceList(tvs: List<VizioTv>, choose: (VizioTv) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Palette.surface) {
        Column {
            tvs.forEachIndexed { index, tv ->
                if (index > 0) HorizontalDivider(color = Palette.raised, thickness = 1.dp)
                SettingsItem(tv.name, Lucide.Cast, subtitle = tv.origin.removePrefix("https://"), action = { choose(tv) })
            }
        }
    }
}

@Composable
private fun VizioSetupManual(compact: Boolean, controller: WatchOnTvSetupController) {
    DeviceTile()
    DeviceTitle("Enter your TV's IP address", compact)
    DeviceSubtitle("Find it on the TV under Settings › Network.", compact)
    AppTextField(
        value = controller.manual,
        onValueChange = controller::manualChanged,
        label = "IP address",
        enabled = !controller.busy,
        isError = controller.manualError != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { controller.connectManual() }),
        modifier = Modifier.fillMaxWidth(),
    )
    controller.manualError?.let { DeviceError(it) }
    DevicePrimaryButton("Connect", controller.manual.isNotBlank() && !controller.busy, controller.busy, controller::connectManual)
    DeviceSecondaryButton("Cancel", controller::cancelManual)
}

@Composable
private fun VizioSetupNoAccess(compact: Boolean, controller: WatchOnTvSetupController) {
    DeviceTile()
    DeviceTitle("VIPTV can't look for TVs", compact)
    DeviceSubtitle("Local network access is off for VIPTV. Turn it on in your phone's settings, then come back here.", compact)
    DevicePrimaryButton("Open phone settings", true, false, controller::openLocalNetworkSettings)
    DeviceSecondaryButton("Try again", controller::retry)
}

@Composable
private fun VizioSetupPin(compact: Boolean, tv: VizioTv, controller: WatchOnTvSetupController) {
    DeviceTile()
    DeviceTitle("Enter the PIN on your TV", compact)
    DeviceSubtitle("${tv.name} is showing a 4-digit PIN.", compact)
    if (!controller.pinReady && controller.busy) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Palette.secondary, strokeWidth = 3.dp)
        DeviceSubtitle("Connecting to ${tv.name}…", compact)
        DeviceSecondaryButton("Cancel") { controller.cancelPin() }
    } else if (!controller.pinReady) {
        controller.pinError?.let { DeviceError(it) }
        DeviceSecondaryButton("Try again") { controller.newPin() }
        DeviceSecondaryButton("Cancel") { controller.cancelPin() }
    } else {
        VizioPinBoxes(controller.pin, enabled = !controller.busy, error = controller.pinError != null) { controller.pinChanged(it) }
        if (controller.busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Palette.secondary, strokeWidth = 2.dp)
            Text("Pairing…", color = Palette.secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else {
            Text("Pairs as soon as all 4 digits are in.", color = Palette.tertiary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        controller.pinError?.let { DeviceError(it) }
        DeviceSecondaryButton("New PIN") { controller.newPin() }
        DeviceSecondaryButton("Cancel") { controller.cancelPin() }
    }
}

@Composable
private fun VizioSetupDone(compact: Boolean, tv: VizioTv, back: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    DeviceCheckTile()
    DeviceTitle("Connected to ${tv.name}", compact)
    DeviceSubtitle("You can control it from this phone now.", compact)
    DevicePrimaryButton("Done", true, false, back)
}

@Composable
private fun VizioPinBoxes(value: String, enabled: Boolean, error: Boolean, changed: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.pointerClickable(interactionSource = interaction, indication = null) { focus.requestFocus() },
    ) {
        repeat(4) { index ->
            val filled = index < value.length
            val active = index == value.length && enabled && !error
            Box(
                Modifier.size(64.dp, 72.dp).background(
                    if (error) Color(0x29FF7A6E) else Color(0x0FFFFFFF),
                    RoundedCornerShape(18.dp),
                ).then(
                    if (error) Modifier.border(1.dp, Palette.danger, RoundedCornerShape(18.dp))
                    else if (active) Modifier.border(2.dp, Palette.text, RoundedCornerShape(18.dp))
                    else Modifier.border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(18.dp)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (filled) Box(Modifier.size(16.dp).background(Palette.text, CircleShape))
            }
        }
    }
    Box(Modifier.width(0.dp).height(0.dp)) {
        AppBasicTextField(
            value = value,
            onValueChange = { raw -> changed(raw.filter(Char::isDigit).take(4)) },
            textStyle = TextStyle(Color.Transparent, fontSize = 1.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            label = "PIN",
            autocomplete = "one-time-code",
            modifier = Modifier.focusRequester(focus).width(0.dp).height(0.dp),
            enabled = enabled,
        )
    }
}