package org.viptv.app.player

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

internal object AndroidMediaSessions {
    private var host: AndroidMediaSessionService? = null
    private var current: MediaSession? = null

    val session: MediaSession? get() = current

    fun publish(context: Context, session: MediaSession) {
        current = session
        host?.addSession(session)
        if (host == null) runCatching { context.startService(intent(context)) }
    }

    fun withdraw(context: Context, session: MediaSession) {
        if (current === session) current = null
        host?.removeSession(session)
        if (current == null) runCatching { context.stopService(intent(context)) }
    }

    internal fun attach(service: AndroidMediaSessionService) {
        host = service
        current?.let(service::addSession)
    }

    internal fun detach(service: AndroidMediaSessionService) { if (host === service) host = null }

    private fun intent(context: Context) = Intent(context, AndroidMediaSessionService::class.java)
}

class AndroidMediaSessionService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = AndroidMediaSessions.session

    override fun onCreate() {
        super.onCreate()
        AndroidMediaSessions.attach(this)
    }

    override fun onDestroy() {
        AndroidMediaSessions.detach(this)
        super.onDestroy()
    }
}
