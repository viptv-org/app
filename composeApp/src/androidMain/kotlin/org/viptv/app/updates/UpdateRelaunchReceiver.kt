package org.viptv.app.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateRelaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val application = context.applicationContext
        val launch = application.packageManager.getLaunchIntentForPackage(application.packageName)
            ?: Intent(Intent.ACTION_MAIN).setClassName(application.packageName, MAIN_ACTIVITY)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launch.putExtra(EXTRA_UPDATED_TO, installedVersionName(application))
        try {
            application.startActivity(launch)
        } catch (_: RuntimeException) {
        }
    }

    companion object {
        // MainActivity reads this once to show "Updated to …".
        const val EXTRA_UPDATED_TO = "org.viptv.app.updates.UPDATED_TO"

        // The launcher activity in androidApp; used only where no launch intent resolves.
        const val MAIN_ACTIVITY = "org.viptv.experiment.MainActivity"

        @Suppress("DEPRECATION")
        internal fun installedVersionName(context: Context): String =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
