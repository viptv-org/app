package org.viptv.app.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.pausePlayback

@UnstableApi
object AndroidPictureInPicture {
    private const val TOGGLE = "org.viptv.app.pictureinpicture.TOGGLE"
    private const val BACK = "org.viptv.app.pictureinpicture.BACK"
    private const val FORWARD = "org.viptv.app.pictureinpicture.FORWARD"
    private const val SKIP_MILLIS = 10_000L

    private class Attached(val activity: Activity, val controller: ApplicationController, val television: Boolean, val deviceSupports: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var session: AndroidPlaybackSession? = null
        var listening = false
    }

    private var attached: Attached? = null

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = refresh()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onTracksChanged(tracks: Tracks) = refresh()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TOGGLE -> attached?.controller?.pausePlayback()
                BACK -> skip(-SKIP_MILLIS)
                FORWARD -> skip(SKIP_MILLIS)
            }
        }
    }

    fun attach(activity: Activity, controller: ApplicationController, television: Boolean) {
        detach()
        val value = Attached(activity, controller, television, activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val filter = IntentFilter(TOGGLE).apply { addAction(BACK); addAction(FORWARD) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else activity.registerReceiver(receiver, filter)
        value.listening = true
        attached = value
        PictureInPictureWindow.set(activity.isInPictureInPictureMode)
        refresh()
    }

    fun detach() {
        val value = attached ?: return
        attached = null
        value.session?.player?.removeListener(listener)
        value.session = null
        if (value.listening) value.activity.unregisterReceiver(receiver)
        value.scope.cancel()
        PictureInPictureWindow.set(false)
    }

    fun modeChanged(active: Boolean) { PictureInPictureWindow.set(active) }

    fun onUserLeaveHint() {
        val value = attached ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (blocker(value) != null) return
        value.activity.enterPictureInPictureMode(params(value))
    }

    internal fun bind(session: AndroidPlaybackSession) {
        val value = attached ?: return
        if (value.session === session) return
        value.session?.player?.removeListener(listener)
        value.session = session
        session.player.addListener(listener)
        refresh()
    }

    internal fun unbind(session: AndroidPlaybackSession) {
        val value = attached ?: return
        if (value.session !== session) return
        session.player.removeListener(listener)
        value.session = null
        refresh()
    }

    private fun refresh() {
        val value = attached ?: return
        if (value.television) return
        value.activity.setPictureInPictureParams(params(value))
    }

    private fun params(value: Attached): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        aspectRatio(value)?.let(builder::setAspectRatio)
        builder.setActions(actions(value))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(blocker(value) == null)
        return builder.build()
    }

    private fun blocker(value: Attached): PictureInPictureBlocker? = pictureInPictureBlocker(television = value.television,
        playing = playing(value), streamSupports = streamSupports(value), deviceSupports = value.deviceSupports)

    private fun playing(value: Attached): Boolean {
        val player = value.session?.player ?: return false
        return player.playWhenReady && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
    }

    private fun streamSupports(value: Attached): Boolean {
        val player = value.session?.player ?: return false
        if (player.videoSize.width <= 0 || player.videoSize.height <= 0) return false
        return player.currentTracks.groups.none { group ->
            group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { group.getTrackFormat(it).drmInitData != null }
        }
    }

    private fun aspectRatio(value: Attached): Rational? {
        val size = value.session?.player?.videoSize ?: return null
        return if (size.width > 0 && size.height > 0) Rational(size.width, size.height) else null
    }

    private fun actions(value: Attached): List<RemoteAction> {
        val context = value.activity
        val toggle = if (value.session?.player?.playWhenReady == true)
            action(context, TOGGLE, 1, android.R.drawable.ic_media_pause, "Pause")
        else action(context, TOGGLE, 1, android.R.drawable.ic_media_play, "Play")
        if (value.session?.seekable != true) return listOf(toggle)
        return listOf(action(context, BACK, 2, android.R.drawable.ic_media_rew, "Back 10 seconds"), toggle,
            action(context, FORWARD, 3, android.R.drawable.ic_media_ff, "Forward 10 seconds"))
    }

    private fun action(context: Context, action: String, code: Int, icon: Int, label: String) = RemoteAction(
        Icon.createWithResource(context, icon), label, label, PendingIntent.getBroadcast(context, code,
            Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

    private fun skip(deltaMillis: Long) {
        val value = attached ?: return
        val session = value.session ?: return
        value.scope.launch { session.skip(deltaMillis) }
    }
}
