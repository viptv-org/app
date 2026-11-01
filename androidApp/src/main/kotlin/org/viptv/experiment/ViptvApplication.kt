package org.viptv.experiment

import android.app.Application
import android.os.Build
import org.viptv.app.crash.AndroidCrashReports
import org.viptv.app.crash.CrashFacts
import org.viptv.app.crash.CrashReport
import org.viptv.app.crash.CrashReportStore

class ViptvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidCrashReports.attach(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                CrashReportStore.storeReport(CrashReport(
                    appVersion = BuildConfig.VERSION_NAME,
                    buildCommit = BuildConfig.BUILD_COMMIT,
                    deviceModel = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    thread = thread.name,
                    stack = error.stackTraceToString(),
                    lastScreen = CrashFacts.lastScreen,
                    playback = CrashFacts.playbackAttempt,
                ))
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }
}