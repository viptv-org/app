package org.viptv.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = AndroidDownloads.engine ?: run { AndroidDownloads.attach(this); AndroidDownloads.engine }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val first = notification(engine?.items?.value.orEmpty())
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION, first)
        if (engine == null) { stopSelf(); return START_NOT_STICKY }
        if (watcher?.isActive != true) watcher = scope.launch {
            var idleSince: Long? = null
            engine.items.collect { items ->
                val busy = items.any { it.state == DownloadState.Resolving || it.state == DownloadState.Downloading || it.state == DownloadState.Queued }
                manager.notify(NOTIFICATION, notification(items))
                if (busy) idleSince = null
                else if (idleSince == null) {
                    idleSince = System.currentTimeMillis()
                    launch {
                        delay(IDLE_MILLIS)
                        if (engine.items.value.none { it.state == DownloadState.Resolving || it.state == DownloadState.Downloading }) {
                            engine.flush()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AndroidDownloads.engine?.flush()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(items: List<DownloadItem>): Notification {
        val active = items.filter { it.state == DownloadState.Downloading || it.state == DownloadState.Resolving }
        val pending = items.count { it.state == DownloadState.Queued || it.state == DownloadState.Resolving || it.state == DownloadState.Downloading }
        val current = active.firstOrNull()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .setContentTitle(current?.let { it.seriesTitle?.let { series -> "$series · ${it.card.title}" } ?: it.card.title } ?: "Downloads")
            .setContentText(if (pending > 1) "$pending downloads left" else "Downloading")
        val total = current?.bytesTotal
        if (current != null && total != null && total > 0) builder.setProgress(1000, (current.bytesDone * 1000 / total).toInt(), false)
        else if (current != null) builder.setProgress(0, 0, true)
        return builder.build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION = 4101
        const val IDLE_MILLIS = 5_000L
    }
}
