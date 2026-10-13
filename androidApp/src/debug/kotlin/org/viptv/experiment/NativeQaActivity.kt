package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import org.viptv.app.App
import org.viptv.app.connection.*
import org.viptv.app.state.*

/** Temporary disposable-QA entry; removed before the normal APK build. */
class NativeQaActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  AndroidSecrets.attach(applicationContext)
  val store = object : RemoteSessionStore {
   private var value: SavedRemoteSession? = null
   override suspend fun read() = value
   override suspend fun save(session: SavedRemoteSession) { value = session }
   override suspend fun clear() { value = null }
  }
  setContent {
   val scope = rememberCoroutineScope()
   val controller = remember { ApplicationController(scope, store) }
   val state by controller.state.collectAsState()
   var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
   LaunchedEffect(Unit) {
    controller.state.first { !it.starting && !it.busy }
    controller.connect(requireNotNull(intent.getStringExtra("qa_origin")),requireNotNull(intent.getStringExtra("qa_user")),requireNotNull(intent.getStringExtra("qa_password")))
    controller.state.first { !it.busy && it.profiles.isNotEmpty() }
    controller.selectProfile(requireNotNull(intent.getStringExtra("qa_profile")))
    controller.state.first { !it.busy && it.selectedProfile != null }
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
   LaunchedEffect(state.paused) { Log.i("VIPTV_NATIVE_QA","paused=${state.paused}") }
   LaunchedEffect(state.presentation) { state.presentation?.let { Log.i("VIPTV_NATIVE_QA","proof=${it.proof} exact=${it.exact} atNanos=${it.atNanos}") } }
   BackHandler { entryBack?.invoke() ?: if(state.screen == Screen.Player) controller.closePlayer() else controller.navigate(Screen.Home) }
   App(television=true,providedController=controller,localDatabase=filesDir.resolve("qa-unopened.sqlite").absolutePath,entryBackChanged={entryBack=it})
  }
 }
}
