package org.viptv.app.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

sealed interface UpdateStatus {
    data object Unchecked : UpdateStatus
    data object NoServer : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Downloading(val feed: UpdateFeed, val bytes: Long, val paused: Boolean = false) : UpdateStatus
    data class Ready(val feed: UpdateFeed) : UpdateStatus
    data class NeedsPermission(val feed: UpdateFeed, val settingsScreen: Boolean) : UpdateStatus
    data class Installing(val feed: UpdateFeed, val bytes: Long = 0, val confirming: Boolean = false) : UpdateStatus
    data class Failed(val message: String, val feed: UpdateFeed? = null,
        val installAttempt: Boolean = false) : UpdateStatus
}

data class AppUpdateState(
    val versionName: String,
    val versionCode: Long,
    val status: UpdateStatus = UpdateStatus.Unchecked,
    val lastCheckMillis: Long? = null,
    val snoozed: Set<Long> = emptySet(),
    val required: Boolean = false,
    val updatedTo: String? = null,
)

enum class InstallResult { Success, Cancelled, Failed }

const val UPDATE_NOTICE_MILLIS = 6_000L

class UpdateVerificationException(message: String) : Exception(message)

interface AppUpdatePlatform {
    val installedVersionCode: Long
    val installedVersionName: String
    fun nowMillis(): Long
    var lastCheckMillis: Long?
    suspend fun fetchFeed(url: String): String
    suspend fun download(feed: UpdateFeed, progress: (bytes: Long) -> Unit)
    suspend fun hasVerifiedDownload(feed: UpdateFeed): Boolean
    fun deleteDownloads(keep: UpdateFeed?)
    fun canInstall(): Boolean
    fun hasInstallPermissionScreen(): Boolean
    fun openInstallPermissionScreen(): Boolean
    val relaunchesAfterUpdate: Boolean
    suspend fun install(feed: UpdateFeed, progress: (bytes: Long) -> Unit = {}, result: (InstallResult) -> Unit)
}

class AppUpdater(
    private val platform: AppUpdatePlatform,
    private val scope: CoroutineScope,
    private val feedUrl: () -> String?,
) {
    private val mutable = MutableStateFlow(AppUpdateState(platform.installedVersionName, platform.installedVersionCode,
        lastCheckMillis = platform.lastCheckMillis))
    val state: StateFlow<AppUpdateState> = mutable.asStateFlow()

    // The one record of whether the player is open; changed only through applyPlaying.
    private var playing = false
    private var check: Job? = null
    private var download: Job? = null
    // Read by progress callbacks on the download thread.
    @Volatile private var downloadGeneration = 0
    private var awaitingPermission = false
    // Held for the session write: a return to the app must not hide the Updating screen mid-install.
    private var installing: Job? = null
    private var notice: Job? = null

    private var status: UpdateStatus
        get() = mutable.value.status
        set(value) { mutable.update { it.copy(status = value) } }

    val relaunchesAfterUpdate: Boolean get() = platform.relaunchesAfterUpdate

    fun nowMillis(): Long = platform.nowMillis()

    fun foreground(playing: Boolean) {
        applyPlaying(playing)
        // A required update asks again on every return, even after Later.
        if (mutable.value.required) mutable.update { it.copy(snoozed = emptySet()) }
        // Back from Android's confirmation without an answer: Install works again, unless the session is still being written.
        if (installing?.isActive != true) (status as? UpdateStatus.Installing)?.let { status = UpdateStatus.Ready(it.feed) }
        if (awaitingPermission && status is UpdateStatus.NeedsPermission) {
            if (platform.canInstall()) { awaitingPermission = false; install() }
        }
        maybeCheck(manual = false)
    }

    fun connectionChanged() = maybeCheck(manual = false)

    fun playingChanged(playing: Boolean) {
        applyPlaying(playing)
        if (!playing) maybeCheck(manual = false)
    }

    private fun applyPlaying(playing: Boolean) {
        this.playing = playing
        val current = status
        if (current !is UpdateStatus.Downloading) return
        if (playing && download != null) {
            cancelDownload()
            status = current.copy(paused = true)
        } else if (!playing && current.paused) startDownload(current.feed)
    }

    fun checkNow() = maybeCheck(manual = true)

    fun install() {
        val feed = status.offeredFeed ?: return
        mutable.update { it.copy(snoozed = it.snoozed - feed.versionCode) }
        if (!platform.canInstall()) {
            status = UpdateStatus.NeedsPermission(feed, platform.hasInstallPermissionScreen())
            return
        }
        status = UpdateStatus.Installing(feed)
        installing = scope.launch {
            try {
                platform.install(feed, progress = { bytes ->
                    mutable.update { state ->
                        val shown = state.status
                        if (shown is UpdateStatus.Installing && shown.feed == feed && !shown.confirming) state.copy(status = shown.copy(bytes = bytes))
                        else state
                    }
                }) { result ->
                    when (result) {
                        InstallResult.Success -> Unit
                        InstallResult.Cancelled -> {
                            status = UpdateStatus.Ready(feed)
                            mutable.update { it.copy(snoozed = it.snoozed + feed.versionCode) }
                        }
                        InstallResult.Failed -> status = UpdateStatus.Failed("Android didn't install the update.", feed, installAttempt = true)
                    }
                }
                mutable.update { state ->
                    val shown = state.status
                    if (shown is UpdateStatus.Installing && shown.feed == feed && !shown.confirming) {
                        state.copy(status = shown.copy(bytes = feed.size, confirming = true))
                    } else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = UpdateStatus.Failed(if (error is UpdateVerificationException) "The download was damaged. VIPTV will try again later."
                    else "Android didn't install the update.", feed, installAttempt = true)
            }
        }
    }

    fun announceUpdated(version: String?) {
        val confirmed = version?.takeIf { it.isNotBlank() } ?: return
        mutable.update { it.copy(updatedTo = confirmed) }
        notice?.cancel()
        notice = scope.launch {
            delay(UPDATE_NOTICE_MILLIS)
            mutable.update { it.copy(updatedTo = null) }
        }
    }

    fun dismissPendingInstall() = later()

    fun openPermissionSettings(): Boolean {
        awaitingPermission = true
        return platform.openInstallPermissionScreen()
    }

    fun later() {
        val feed = status.offeredFeed ?: return
        if (status !is UpdateStatus.Failed) status = UpdateStatus.Ready(feed)
        awaitingPermission = false
        mutable.update { it.copy(snoozed = it.snoozed + feed.versionCode) }
    }

    private fun maybeCheck(manual: Boolean) {
        if (check?.isActive == true) return
        val url = feedUrl()
        if (url == null) {
            if (!status.holdsDownload()) status = UpdateStatus.NoServer
            return
        }
        if (!updateCheckDue(mutable.value.lastCheckMillis, platform.nowMillis(), playing, manual)) return
        val previous = status
        if (!previous.holdsDownload()) status = UpdateStatus.Checking
        check = scope.launch {
            val decision = try {
                decideUpdate(parseUpdateFeed(platform.fetchFeed(url), url), platform.installedVersionCode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!previous.holdsDownload()) status = UpdateStatus.Failed(checkFailure(error))
                return@launch
            }
            // Only a check that reached the feed starts the hourly limit.
            val checked = platform.nowMillis()
            platform.lastCheckMillis = checked
            mutable.update { it.copy(lastCheckMillis = checked) }
            when (decision) {
                UpdateDecision.UpToDate -> {
                    cancelDownload()
                    platform.deleteDownloads(keep = null)
                    mutable.update { it.copy(status = UpdateStatus.UpToDate, required = false) }
                }
                is UpdateDecision.Available -> {
                    mutable.update { it.copy(required = decision.required) }
                    offer(decision.feed)
                }
            }
        }
    }

    private suspend fun offer(feed: UpdateFeed) {
        val current = status
        if (current.holdsDownload() && current.feedOrNull() == feed) return
        cancelDownload()
        platform.deleteDownloads(keep = feed)
        if (platform.hasVerifiedDownload(feed)) status = UpdateStatus.Ready(feed)
        else startDownload(feed)
    }

    private fun startDownload(feed: UpdateFeed) {
        if (playing) { status = UpdateStatus.Downloading(feed, 0, paused = true); return }
        val generation = ++downloadGeneration
        status = UpdateStatus.Downloading(feed, 0)
        download = scope.launch {
            var attempt = 0
            while (true) {
                try {
                    platform.download(feed) { bytes ->
                        if (generation == downloadGeneration) mutable.update { state ->
                            val shown = state.status
                            if (shown is UpdateStatus.Downloading && shown.feed == feed) state.copy(status = shown.copy(bytes = bytes)) else state
                        }
                    }
                    if (generation == downloadGeneration) status = UpdateStatus.Ready(feed)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: UpdateVerificationException) {
                    status = UpdateStatus.Failed("The download was damaged. VIPTV will try again later.", feed)
                    return@launch
                } catch (_: Exception) {
                    attempt++
                    val wait = downloadRetryDelayMillis(attempt)
                    if (wait == null) {
                        status = UpdateStatus.Failed("Couldn't download the update. VIPTV will try again later.", feed)
                        return@launch
                    }
                    delay(wait)
                }
            }
        }
    }

    private fun cancelDownload() {
        downloadGeneration++
        download?.cancel()
        download = null
    }

    private fun checkFailure(error: Exception) = when (error) {
        is UpdateFeedException -> if (error.missing) "This server doesn't publish app updates." else "The server's update information is invalid."
        else -> "Couldn't check for updates. Check your connection and try again."
    }
}

val UpdateStatus.offeredFeed: UpdateFeed?
    get() = when (this) {
        is UpdateStatus.Ready -> feed
        is UpdateStatus.NeedsPermission -> feed
        is UpdateStatus.Failed -> feed?.takeIf { installAttempt }
        else -> null
    }

val UpdateStatus.promptFeed: UpdateFeed?
    get() = when (this) {
        is UpdateStatus.Ready -> feed
        is UpdateStatus.Failed -> feed?.takeIf { installAttempt }
        else -> null
    }

private fun UpdateStatus.holdsDownload() = this is UpdateStatus.Downloading || this is UpdateStatus.Ready ||
    this is UpdateStatus.NeedsPermission || this is UpdateStatus.Installing

private fun UpdateStatus.feedOrNull(): UpdateFeed? = when (this) {
    is UpdateStatus.Downloading -> feed
    is UpdateStatus.Ready -> feed
    is UpdateStatus.NeedsPermission -> feed
    is UpdateStatus.Installing -> feed
    is UpdateStatus.Failed -> feed
    else -> null
}
