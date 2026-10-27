package org.viptv.app.updates

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** app#64: a fake feed on loopback, the real Android download, verification and PackageInstaller session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAppUpdateTest {
    private val application = RuntimeEnvironment.getApplication()
    private val apk = Random(64).nextBytes(300_000)
    private var feedSha = sha(apk)
    private val ranges = mutableListOf<String?>()
    /** Bytes the next APK response sends before dropping the connection; null sends all. */
    private var cutAfter: Int? = null
    private lateinit var server: HttpServer
    private lateinit var feedUrl: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before fun serve() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/updates/android/update.json") { exchange ->
            val body = """{"version_code":999999,"version_name":"0.1.999+test","url":"viptv-999999.apk","sha256":"$feedSha","size":${apk.size}}"""
                .encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/updates/android/viptv-999999.apk") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            synchronized(ranges) { ranges += range }
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            exchange.sendResponseHeaders(if (start > 0) 206 else 200, (apk.size - start).toLong())
            val cut = cutAfter.also { cutAfter = null }
            exchange.responseBody.use { out -> out.write(apk, start, (cut ?: (apk.size - start))) }
            exchange.close()
        }
        server.start()
        feedUrl = "http://127.0.0.1:${server.address.port}/updates/android/update.json"
    }

    @After fun stop() { scope.cancel(); server.stop(0) }

    private fun platform() = AndroidAppUpdatePlatform(application)
    private fun feed(platform: AndroidAppUpdatePlatform) = runBlocking { parseUpdateFeed(platform.fetchFeed(feedUrl), feedUrl) }

    @Test fun theUpdaterDownloadsVerifiesAndHandsTheBuildToThePackageInstaller() {
        val platform = platform()
        val updater = AppUpdater(platform, scope) { feedUrl }
        updater.foreground(playing = false)
        val ready = assertIs<UpdateStatus.Ready>(awaitStatus(updater) { it is UpdateStatus.Ready || it is UpdateStatus.Failed })
        assertEquals("http://127.0.0.1:${server.address.port}/updates/android/viptv-999999.apk", ready.feed.url)

        shadowOf(application.packageManager).setCanRequestPackageInstalls(true)
        val installer = application.packageManager.packageInstaller
        val created = mutableListOf<Int>()
        val finished = mutableListOf<Boolean>()
        installer.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) { created += sessionId }
            override fun onBadgingChanged(sessionId: Int) = Unit
            override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit
            override fun onProgressChanged(sessionId: Int, progress: Float) = Unit
            override fun onFinished(sessionId: Int, success: Boolean) { finished += success }
        }, android.os.Handler(Looper.getMainLooper()))
        updater.install()
        // Robolectric commits a session at once; on a device Android now shows its confirmation.
        awaitStatus(updater) { finished.isNotEmpty() }
        assertEquals(1, created.size, "One PackageInstaller session for the verified APK")
        assertEquals(listOf(true), finished, "The session was written and committed")

        // Android asks the viewer to confirm: the updater opens the installer's own confirmation.
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        application.sendBroadcast(Intent(application.packageName + ".UPDATE_INSTALL_STATUS").setPackage(application.packageName)
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", shadowOf(application).nextStartedActivity?.action)
    }

    @Test fun theInstallIsRefusedWhenTheFileChangedAfterVerification() {
        val platform = platform()
        val feed = feed(platform)
        runBlocking { platform.download(feed) {} }
        platform.directory.listFiles()!!.single().appendBytes(byteArrayOf(1))
        assertFailsWith<UpdateVerificationException> { runBlocking { platform.install(feed) {} } }
        assertTrue(application.packageManager.packageInstaller.allSessions.isEmpty())
    }

    @Test fun aChecksumMismatchIsRejectedAndNothingIsKept() {
        feedSha = "0".repeat(64)
        val platform = platform()
        val feed = feed(platform)
        assertFailsWith<UpdateVerificationException> { runBlocking { platform.download(feed) {} } }
        assertTrue(platform.directory.listFiles().orEmpty().isEmpty(), "No partial or complete file is left")
        assertTrue(!runBlocking { platform.hasVerifiedDownload(feed) })
    }

    @Test fun anInterruptedDownloadResumesFromWhereItStopped() {
        val platform = platform()
        val feed = feed(platform)
        cutAfter = 100_000
        assertFailsWith<Exception> { runBlocking { platform.download(feed) {} } }
        runBlocking { platform.download(feed) {} }
        assertEquals(listOf(null, "bytes=100000-"), ranges)
        assertTrue(runBlocking { platform.hasVerifiedDownload(feed) })
    }

    @Test fun staleDownloadsAreDeleted() {
        val platform = platform()
        val feed = feed(platform)
        runBlocking { platform.download(feed) {} }
        File(platform.directory, "viptv-1-old.apk").writeText("old")
        platform.deleteDownloads(keep = feed)
        assertEquals(1, platform.directory.listFiles()?.size)
        platform.deleteDownloads(keep = null)
        assertTrue(platform.directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun theLastCheckTimeIsPersisted() {
        platform().lastCheckMillis = 1234
        assertEquals(1234, platform().lastCheckMillis)
    }

    private fun awaitStatus(updater: AppUpdater, done: (UpdateStatus) -> Boolean): UpdateStatus {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done(updater.state.value.status) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        return updater.state.value.status
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
