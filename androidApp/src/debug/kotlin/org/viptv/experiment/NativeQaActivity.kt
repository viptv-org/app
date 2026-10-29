package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.viptv.app.App
import org.viptv.app.connection.*
import org.viptv.app.state.*

class NativeQaActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  AndroidSecrets.attach(applicationContext)
  val vault = AndroidSecrets.vault()
  val localPlaylist = intent.getStringExtra("qa_local_playlist")
  val requestedOrigin = intent.getStringExtra("qa_origin")?.trimEnd('/')
  val store = object : RemoteSessionStore {
   override suspend fun read() = if (localPlaylist != null) null else vault.get("qa:remote-session")?.let {
    Json.decodeFromString<SavedRemoteSession>(it).takeIf { saved -> requestedOrigin == null || saved.origin.trimEnd('/') == requestedOrigin }
   }
   override suspend fun save(session: SavedRemoteSession) { vault.put("qa:remote-session", Json.encodeToString(session)) }
   override suspend fun clear() { if (localPlaylist == null) vault.remove("qa:remote-session") }
  }
  setContent {
   val scope = rememberCoroutineScope()
   val controller = remember { ApplicationController(scope, store, playbackDiagnostics = { Log.i("VIPTV_PLAYBACK_QA", it) }) }
   val state by controller.state.collectAsState()
   var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
   LaunchedEffect(Unit) {
    controller.state.first { !it.starting && !it.busy }
    if (localPlaylist != null) {
     controller.prepareLocalLive(filesDir.resolve("qa-public-local-live.sqlite").absolutePath, localPlaylist)
     return@LaunchedEffect
    }
    if (controller.state.value.profiles.isEmpty()) {
     controller.connect(requireNotNull(intent.getStringExtra("qa_origin")),requireNotNull(intent.getStringExtra("qa_user")),requireNotNull(intent.getStringExtra("qa_password")))
     controller.state.first { !it.busy && it.profiles.isNotEmpty() }
    }
    if (controller.state.value.selectedProfile != intent.getStringExtra("qa_profile"))
     controller.selectProfile(requireNotNull(intent.getStringExtra("qa_profile")))
    controller.state.first { !it.busy && it.selectedProfile != null }
    intent.getStringExtra("qa_screen")?.let { destination ->
     Screen.entries.firstOrNull { it.name.equals(destination, true) }?.let(controller::navigate)
    }
    intent.getStringExtra("qa_search")?.let { controller.navigate(Screen.Search); controller.search(it) }
    val kind = intent.getStringExtra("qa_kind")
    val id = intent.getStringExtra("qa_id")
    if (kind != null && id != null) controller.openRemoteIdentity(kind, id)
    if (intent.getBooleanExtra("qa_auto_play", false)) {
     val detail = controller.state.first { !it.busy && it.detail != null }.detail!!
     val target = detail.episodes.firstOrNull() ?: detail.item
     controller.chooseSource(target)
     val sources = controller.state.first { it.sources.isNotEmpty() }.sources
     val source = sources.firstOrNull { it.label.contains("Pengu", true) && it.label.contains("1080", true) }
      ?: sources.firstOrNull { it.label.contains("Pengu", true) }
     if (source == null) { Log.w("VIPTV_NATIVE_QA", "Preferred provider unavailable; total=${sources.size}"); return@LaunchedEffect }
     controller.playSource(source, forceGateway = intent.getBooleanExtra("qa_force_gateway", false))
    }
   }
   LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, true); Log.i("VIPTV_NATIVE_QA","screen=${state.screen} error=${state.error}") }
   LaunchedEffect(state.error) { state.error?.let { Log.w("VIPTV_NATIVE_QA", "error=$it") } }
   LaunchedEffect(state.starting, state.busy, state.screen, state.detail?.item?.identity) {
    val ready = !state.starting && !state.busy && (state.screen != Screen.Details || state.detail != null)
    Log.i("VIPTV_NATIVE_QA", "ready=$ready screen=${state.screen}")
   }
   LaunchedEffect(state.paused) { Log.i("VIPTV_NATIVE_QA","paused=${state.paused}") }
   LaunchedEffect(state.presentation) { state.presentation?.let { Log.i("VIPTV_NATIVE_QA","proof=${it.proof} exact=${it.exact} atNanos=${it.atNanos}") } }
   BackHandler { entryBack?.invoke() ?: when (state.screen) {
    Screen.Player -> controller.closePlayer()
    Screen.Details -> controller.backDetails()
    Screen.Sources -> controller.backSources()
    else -> controller.navigate(Screen.Home)
   } }
   val qaPlatform = intent.getStringExtra("qa_platform") ?: "tv"
   App(television=qaPlatform == "tv",browser=qaPlatform == "web",desktopChrome=qaPlatform == "desktop",providedController=controller,imageDiagnostics={Log.i("VIPTV_IMAGE_QA",it)},localDatabase=filesDir.resolve("qa-unopened.sqlite").absolutePath,entryBackChanged={entryBack=it})
  }
 }
}
