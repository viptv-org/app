package org.viptv.app.updates

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android side of [AppUpdater]: plain HTTPS, APKs under `filesDir/updates`, and a
 * [PackageInstaller] session that asks the viewer to confirm. Android itself refuses an
 * APK signed with another key or with a lower version code, so app data and sign-in stay.
 */
class AndroidAppUpdatePlatform(context: Context) : AppUpdatePlatform {
    private val application = context.applicationContext
    private val preferences = application.getSharedPreferences("viptv.updates", Context.MODE_PRIVATE)
    internal val directory = File(application.filesDir, "updates")
    private val installAction = application.packageName + ".UPDATE_INSTALL_STATUS"
    private var pendingResult: ((InstallResult) -> Unit)? = null
    private var receiverRegistered = false

    private val packageInfo get() = application.packageManager.getPackageInfo(application.packageName, 0)
    override val installedVersionCode: Long
        get() = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
    override val installedVersionName: String get() = packageInfo.versionName.orEmpty()

    override fun nowMillis() = System.currentTimeMillis()
    override var lastCheckMillis: Long?
        get() = preferences.getLong("last_check", -1).takeIf { it >= 0 }
        set(value) { preferences.edit().apply { if (value == null) remove("last_check") else putLong("last_check", value) }.apply() }

    override suspend fun fetchFeed(url: String): String = withContext(Dispatchers.IO) {
        val connection = open(url)
        try {
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw UpdateFeedException("No update feed at $url", missing = true)
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Update feed returned HTTP $code")
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_FEED_BYTES + 1)
                if (bytes.size > MAX_FEED_BYTES) throw IOException("Update feed is too large")
                bytes.decodeToString()
            }
        } finally { connection.disconnect() }
    }

    override suspend fun download(feed: UpdateFeed, progress: (bytes: Long) -> Unit) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = apkFile(feed)
        val partial = partialFile(feed)
        if (partial.length() > feed.size) partial.delete()
        val offset = partial.length()
        val connection = open(feed.url)
        try {
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val code = connection.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> offset > 0
                HttpURLConnection.HTTP_OK -> false
                // The partial file is already complete, or the server lost track of it: start over next time.
                416 -> { partial.delete(); throw IOException("Range not satisfiable") }
                else -> throw IOException("Update download returned HTTP $code")
            }
            var written = if (append) offset else 0L
            progress(written)
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > feed.size) { partial.delete(); throw UpdateVerificationException("Larger than the feed's size") }
                        output.write(buffer, 0, count)
                        progress(written)
                    }
                }
            }
            // Short: the connection dropped. Keep the partial file so the retry resumes it.
            if (written < feed.size) throw IOException("Update download ended at $written of ${feed.size} bytes")
        } finally { connection.disconnect() }
        if (!downloadMatches(feed, partial.length(), sha256(partial))) {
            partial.delete()
            throw UpdateVerificationException("Size or SHA-256 does not match the feed")
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not store the update")
    }

    override suspend fun hasVerifiedDownload(feed: UpdateFeed): Boolean = withContext(Dispatchers.IO) {
        val file = apkFile(feed)
        file.isFile && downloadMatches(feed, file.length(), sha256(file))
    }

    override fun deleteDownloads(keep: UpdateFeed?) {
        val kept = keep?.let { setOf(apkFile(it).name, partialFile(it).name) }.orEmpty()
        directory.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
    }

    override fun canInstall(): Boolean = application.packageManager.canRequestPackageInstalls()

    private fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + application.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun hasInstallPermissionScreen(): Boolean = permissionIntent().resolveActivity(application.packageManager) != null

    override fun openInstallPermissionScreen(): Boolean = try {
        application.startActivity(permissionIntent()); true
    } catch (_: RuntimeException) { false }

    override suspend fun install(feed: UpdateFeed, result: (InstallResult) -> Unit) {
        val apk = apkFile(feed)
        // The file sat on disk since it was verified; check it again right before Android reads it.
        if (!hasVerifiedDownload(feed)) { apk.delete(); throw UpdateVerificationException("The downloaded update changed on disk") }
        registerReceiver()
        pendingResult = result
        withContext(Dispatchers.IO) {
            val installer = application.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(application.packageName)
                setSize(apk.length())
            }
            val id = installer.createSession(params)
            try {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output, 64 * 1024) }
                        session.fsync(output)
                    }
                    val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val status = PendingIntent.getBroadcast(application, id, Intent(installAction).setPackage(application.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                    session.commit(status.intentSender)
                }
            } catch (error: Exception) {
                Log.w("VIPTV_UPDATE", "Could not start the install session", error)
                runCatching { installer.abandonSession(id) }
                pendingResult = null
                throw error
            }
        }
    }

    private val installStatus = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm == null) finish(InstallResult.Failed)
                    else try { application.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (_: RuntimeException) { finish(InstallResult.Failed) }
                }
                PackageInstaller.STATUS_SUCCESS -> finish(InstallResult.Success)
                PackageInstaller.STATUS_FAILURE_ABORTED -> finish(InstallResult.Cancelled)
                else -> {
                    Log.w("VIPTV_UPDATE", "Install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                    finish(InstallResult.Failed)
                }
            }
        }
    }

    private fun finish(result: InstallResult) {
        val callback = pendingResult ?: return
        pendingResult = null
        callback(result)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        val filter = IntentFilter(installAction)
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(installStatus, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(installStatus, filter)
    }

    private fun apkFile(feed: UpdateFeed) = File(directory, "viptv-${feed.versionCode}-${feed.sha256.take(12)}.apk")
    private fun partialFile(feed: UpdateFeed) = File(directory, apkFile(feed).name + ".part")

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
    }

    private companion object { const val MAX_FEED_BYTES = 64 * 1024 }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (count < 0) break
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
