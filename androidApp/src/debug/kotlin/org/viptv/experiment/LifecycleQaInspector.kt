package org.viptv.experiment

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Job
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.ApplicationController

/** Debug-only reads avoid adding test controls to the production shared controller. */
@UnstableApi
internal object LifecycleQaInspector {
    private val progress = ApplicationController::class.java.getDeclaredField("lastProgress").apply { isAccessible = true }
    fun progress(controller: ApplicationController) = progress.get(controller) as? PlayerFact.Progress
    fun player(activity: MainActivity): ExoPlayer? {
        val type = Class.forName("org.viptv.app.player.AndroidPlaybackModel").asSubclass(ViewModel::class.java)
        val model = ViewModelProvider(activity)[type]
        val session = type.getDeclaredField("session").apply { isAccessible = true }.get(model) ?: return null
        return session.javaClass.getMethod("getPlayer").invoke(session) as ExoPlayer
    }
    // Invoke the actual suspend command handler without adding a production test API.
    suspend fun command(activity: MainActivity, command: org.viptv.app.player.PlayerCommand): Unit =
        kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation ->
            val type = Class.forName("org.viptv.app.player.AndroidPlaybackModel").asSubclass(ViewModel::class.java)
            val model = ViewModelProvider(activity)[type]
            val session = requireNotNull(type.getDeclaredField("session").apply { isAccessible = true }.get(model))
            session.javaClass.getMethod("command", org.viptv.app.player.PlayerCommand::class.java,
                kotlin.coroutines.Continuation::class.java).invoke(session, command, continuation)
        }
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    fun playerSemantics(activity: MainActivity): JSONObject {
        val roots = mutableListOf<ViewRootForTest>()
        fun visit(view: View) {
            if (view is ViewRootForTest) roots += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(activity.window.decorView)
        val nodes = roots.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false) }
        val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { value -> value.text } }
        val sliders = nodes.mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { range ->
                JSONObject().put("current", range.current.toDouble()).put("min", range.range.start.toDouble())
                    .put("max", range.range.endInclusive.toDouble()).put("steps", range.steps)
                    .put("bounds", node.boundsInRoot.toString())
            }
        }
        return JSONObject().put("texts", JSONArray(texts)).put("sliders", JSONArray(sliders))
    }
    suspend fun flushProgress(controller: ApplicationController) {
        val type = Class.forName("org.viptv.app.state.ControllerProgressKt")
        val method = type.getDeclaredMethod("saveProgress", ApplicationController::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        (method.invoke(null, controller, false, false) as? Job)?.join()
    }
    fun press(activity: MainActivity, code: Int) {
        activity.window.decorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.window.decorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
