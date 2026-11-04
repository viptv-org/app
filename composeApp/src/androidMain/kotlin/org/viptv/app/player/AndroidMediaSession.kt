package org.viptv.app.player

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

internal class AndroidMediaSession(context: Context, player: Player, seek: (Long) -> Unit) {
    private val seekPlayer = object : ForwardingPlayer(player) {
        override fun seekTo(positionMs: Long) { seek(positionMs) }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) { seek(positionMs) }
    }
    val session: MediaSession = MediaSession.Builder(context, seekPlayer)
        .apply { launchIntent(context)?.let(::setSessionActivity) }
        .build()
    val player: Player get() = seekPlayer

    fun close() { session.release() }
}

private fun launchIntent(context: Context): PendingIntent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
