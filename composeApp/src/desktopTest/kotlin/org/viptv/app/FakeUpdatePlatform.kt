package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import org.viptv.app.updates.*

internal val FAKE_UPDATE_SHA = "ab".repeat(32)

internal fun feedJson(code: Long, minimum: Long? = null) = """{"version_code":$code,"version_name":"0.1.$code","url":"viptv-$code.apk",""" +
    """"sha256":"$FAKE_UPDATE_SHA","size":100${minimum?.let { ",\"min_supported_version_code\":$it" } ?: ""}}"""

/** Updater platform for desktop tests: the installed build is 1300, everything else is scripted. */
internal class FakeUpdatePlatform(var feed: String) : AppUpdatePlatform {
    override val installedVersionCode = 1300L
    override val installedVersionName = "0.1.300+aaaaaaa"
    var now = 100 * 3_600_000L
    override fun nowMillis() = now
    override var lastCheckMillis: Long? = null
    val fetched = mutableListOf<String>()
    var fetchFails = false
    /** Each download call takes the next outcome; null succeeds. */
    val downloadOutcomes = ArrayDeque<Exception?>()
    var downloadGate: CompletableDeferred<Unit>? = null
    var downloads = 0
    val verified = mutableSetOf<Long>()
    val kept = mutableListOf<UpdateFeed?>()
    var installAllowed = true
    var permissionScreen = true
    var permissionOpened = 0
    val installs = mutableListOf<UpdateFeed>()
    var installResult: ((InstallResult) -> Unit)? = null

    override suspend fun fetchFeed(url: String): String {
        fetched += url
        if (fetchFails) throw IllegalStateException("offline")
        return feed
    }
    override suspend fun download(feed: UpdateFeed, progress: (Long) -> Unit) {
        downloads++
        progress(50)
        downloadGate?.await()
        downloadOutcomes.removeFirstOrNull()?.let { throw it }
        verified += feed.versionCode
        progress(feed.size)
    }
    override suspend fun hasVerifiedDownload(feed: UpdateFeed) = feed.versionCode in verified
    override fun deleteDownloads(keep: UpdateFeed?) { kept += keep; verified.retainAll(setOfNotNull(keep?.versionCode)) }
    override fun canInstall() = installAllowed
    override fun hasInstallPermissionScreen() = permissionScreen
    override fun openInstallPermissionScreen(): Boolean { permissionOpened++; return permissionScreen }
    override suspend fun install(feed: UpdateFeed, result: (InstallResult) -> Unit) { installs += feed; installResult = result }
}

