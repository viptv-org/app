package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.App
import org.viptv.app.connection.AndroidSecrets
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.*

/** QA-only source qualification in an isolated local profile; optional playback never uses account tracking. */
class NativeSourceQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        org.viptv.app.connection.observeAddonFetches { Log.i("VIPTV_ADDON_QA", it) }
        val database = cacheDir.resolve("source-qa-${java.util.UUID.randomUUID()}.sqlite").absolutePath
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { ApplicationController(scope, object : RemoteSessionStore {
                override suspend fun read(): SavedRemoteSession? = null
                override suspend fun save(session: SavedRemoteSession) = Unit
                override suspend fun clear() = Unit
            }, playbackDiagnostics = { Log.i("VIPTV_PLAYBACK_QA", it) }) }
            val state by controller.state.collectAsState()
            var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
            LaunchedEffect(Unit) {
                controller.state.first { !it.starting && !it.busy }
                controller.useLocal(database)
                controller.state.first { !it.busy && it.profiles.isNotEmpty() }
                controller.selectProfile("default")
                controller.state.first { !it.busy && it.selectedProfile != null }
                controller.installAddon("https://v3-cinemeta.strem.io/manifest.json")
                controller.state.first { !it.busy }
                controller.refreshSourceIntegrations()
                controller.state.first { !it.busy }
                val key = intent.getStringExtra("qa_tmdb_key") ?: return@LaunchedEffect
                intent.removeExtra("qa_tmdb_key")
                controller.configureSourceIntegration("vsources", buildJsonObject {
                    put("tmdb_api_key", key)
                    intent.getStringExtra("qa_provider")?.let { put("providers", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(it)))) }
                })
                controller.state.first { !it.busy }
                controller.search("Ted Lasso")
                val results = controller.state.first { !it.busy }.results
                val card = results.firstOrNull { it.title == "Ted Lasso" } ?: return@LaunchedEffect
                controller.open(card)
                val detail = controller.state.first { !it.busy && it.detail != null }.detail!!
                controller.chooseSource(detail.episodes.firstOrNull { it.identity.episode?.season == 1L && it.identity.episode?.episode == 1L } ?: detail.item)
                controller.state.first { !it.findingSources }
                Log.i("VIPTV_SOURCE_QA", "standalone=true sources=${controller.state.value.sources.size} error=${controller.state.value.error != null}")
                if (intent.getBooleanExtra("qa_auto_play", false)) {
                    val index = intent.getIntExtra("qa_source_index", 0)
                    controller.state.value.sources.getOrNull(index)?.let { source ->
                        Log.i("VIPTV_SOURCE_QA", "selected_index=$index quality_height=${source.quality_height} provider=${source.presentation?.provider_name}")
                        controller.playSource(source)
                    }
                }
            }
            LaunchedEffect(state.home) { state.home?.let { home ->
                Log.i("VIPTV_SOURCE_QA", "home_ready_rows=${home.rows.count { it.state == org.viptv.engine.wire.RowState.READY }} home_cards=${home.rows.sumOf { it.cards.size }}")
            } }
            LaunchedEffect(state.presentation) { state.presentation?.let {
                Log.i("VIPTV_SOURCE_QA", "presentation=${it.proof} exact=${it.exact}")
            } }
            LaunchedEffect(state.error) { if (state.error != null) Log.w("VIPTV_SOURCE_QA", "client_error=true") }
            LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, true) }
            BackHandler { entryBack?.invoke() ?: when (state.screen) {
                Screen.Player -> controller.closePlayer()
                Screen.Sources -> controller.backSources()
                Screen.Details -> controller.backDetails()
                else -> controller.navigate(Screen.Home)
            } }
            App(television = true, providedController = controller, localDatabase = database, entryBackChanged = { entryBack = it })
        }
    }
}
