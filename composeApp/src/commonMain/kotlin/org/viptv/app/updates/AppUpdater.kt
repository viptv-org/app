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

/** Where the updater is; Settings › About shows it and the prompt reads it. */
sealed interface UpdateStatus {
    /** No check has run in this session. */
    data object Unchecked : UpdateStatus
    /** Not signed in to a server and no feed override: there is nowhere to look. */
    data object NoServer : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    /** [paused]: the player is open; the download continues when it closes. */
    data class Downloading(val feed: UpdateFeed, val bytes: Long, val paused: Boolean = false) : UpdateStatus
    /** A verified APK is on disk. */
    data class Ready(val feed: UpdateFeed) : UpdateStatus
    /** Android needs "Install unknown apps" for VIPTV; [settingsScreen] is false where no such screen exists (some Fire TVs). */
    data class NeedsPermission(val feed: UpdateFeed, val settingsScreen: Boolean) : UpdateStatus
    /** Android's install confirmation is showing. */
    data class Installing(val feed: UpdateFeed) : UpdateStatus
    data class Failed(val message: String, val feed: UpdateFeed? = null) : UpdateStatus
}

data class AppUpdateState(
    val versionName: String,
    val versionCode: Long,
    val status: UpdateStatus = UpdateStatus.Unchecked,
    val lastCheckMillis: Long? = null,
    /** Versions put off with Later in this session. */
    val snoozed: Set<Long> = emptySet(),
    /** The available build is above `min_supported_version_code` of the installed one. */
    val required: Boolean = false,
)

enum class InstallResult { Success, Cancelled, Failed }

/** The downloaded file's size or SHA-256 differs from the feed; it has been deleted. */
class UpdateVerificationException(message: String) : Exception(message)

/** Platform side of the updater: HTTP, app-private storage and Android's package installer. */
interface AppUpdatePlatform {
    val installedVersionCode: Long
    val installedVersionName: String
    fun nowMillis(): Long
    /** Persisted, so the hourly limit holds across launches. */
    var lastCheckMillis: Long?
    suspend fun fetchFeed(url: String): String
    /**
     * Downloads [feed] into app-private storage, resuming a partial file, and verifies its size and
     * SHA-256. Throws [UpdateVerificationException] on a mismatch, any other exception on failure.
     */
    suspend fun download(feed: UpdateFeed, progress: (bytes: Long) -> Unit)
    /** A complete, verified download of [feed] is already on disk. */
    suspend fun hasVerifiedDownload(feed: UpdateFeed): Boolean
    /** Deletes every downloaded or partial APK except [keep]'s. */
    fun deleteDownloads(keep: UpdateFeed?)
    fun canInstall(): Boolean
    fun hasInstallPermissionScreen(): Boolean
    /** Opens the system "Install unknown apps" screen for this app; false when it could not. */
    fun openInstallPermissionScreen(): Boolean
    /**
     * Hands the verified APK to Android's installer, which asks the viewer to confirm; [result]
     * arrives later on the main thread. Throws when the session could not be started.
     */
    suspend fun install(feed: UpdateFeed, result: (InstallResult) -> Unit)
}

/**
 * Checks the update feed, downloads a newer build quietly and offers it ("Update ready: Install").
 * Checks happen on launch, on return to the app and when the player closes, at most once an hour,
 * never while playing; a download pauses while the player is open. Call on the main thread.
 */
class AppUpdater(
    private val platform: AppUpdatePlatform,
    private val scope: CoroutineScope,
    /** The feed address, or null in local mode / signed out (see [updateFeedUrl]). */
    private val feedUrl: () -> String?,
) {
    private val mutable = MutableStateFlow(AppUpdateState(platform.installedVersionName, platform.installedVersionCode,
        lastCheckMillis = platform.lastCheckMillis))
    val state: StateFlow<AppUpdateState> = mutable.asStateFlow()

    /** The one record of whether the player is open; changed only through [applyPlaying]. */
    private var playing = false
    private var check: Job? = null
    private var download: Job? = null
    /** Read by progress callbacks on the download thread. */
    @Volatile private var downloadGeneration = 0
    private var awaitingPermission = false

    private var status: UpdateStatus
        get() = mutable.value.status
        set(value) { mutable.update { it.copy(status = value) } }

    fun nowMillis(): Long = platform.nowMillis()

    /** The app came to the front (and on launch). */
    fun foreground(playing: Boolean) {
        applyPlaying(playing)
        // A required update asks again on every return, even after Later.
        if (mutable.value.required) mutable.update { it.copy(snoozed = emptySet()) }
        // Back from Android's confirmation without an answer (Home, or the task switcher): Install works again.
        (status as? UpdateStatus.Installing)?.let { status = UpdateStatus.Ready(it.feed) }
        if (awaitingPermission && status is UpdateStatus.NeedsPermission) {
            if (platform.canInstall()) { awaitingPermission = false; install() }
        }
        maybeCheck(manual = false)
    }

    /** Signed in, out or to another server: a due check runs against the new feed. */
    fun connectionChanged() = maybeCheck(manual = false)

    /** The player opened or closed: a download waits while it is open, and a due check runs after. */
    fun playingChanged(playing: Boolean) {
        applyPlaying(playing)
        if (!playing) maybeCheck(manual = false)
    }

    /** Pauses a running download when the player opens, and continues a paused one whenever it is closed. */
    private fun applyPlaying(playing: Boolean) {
        this.playing = playing
        val current = status
        if (current !is UpdateStatus.Downloading) return
        if (playing && download != null) {
            cancelDownload()
            status = current.copy(paused = true)
        } else if (!playing && current.paused) startDownload(current.feed)
    }

    /** Settings › About › Check for updates. */
    fun checkNow() = maybeCheck(manual = true)

    /** "Install" on the prompt or in Settings. */
    fun install() {
        val feed = status.offeredFeed ?: return
        mutable.update { it.copy(snoozed = it.snoozed - feed.versionCode) }
        if (!platform.canInstall()) {
            status = UpdateStatus.NeedsPermission(feed, platform.hasInstallPermissionScreen())
            return
        }
        status = UpdateStatus.Installing(feed)
        scope.launch {
            try {
                platform.install(feed) { result ->
                    when (result) {
                        // Android replaces and restarts the app; nothing to do here.
                        InstallResult.Success -> Unit
                        // The viewer backed out of Android's confirmation: as if they chose Later.
                        InstallResult.Cancelled -> {
                            status = UpdateStatus.Ready(feed)
                            mutable.update { it.copy(snoozed = it.snoozed + feed.versionCode) }
                        }
                        InstallResult.Failed -> status = UpdateStatus.Failed("Android didn't install the update.", feed)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = UpdateStatus.Failed(if (error is UpdateVerificationException) "The download was damaged. VIPTV will try again later."
                    else "Android didn't install the update.", feed)
            }
        }
    }

    /** Opens the system permission screen; the install continues when the viewer comes back with it allowed. */
    fun openPermissionSettings(): Boolean {
        awaitingPermission = true
        return platform.openInstallPermissionScreen()
    }

    /** "Later": hides the prompt for this version until the app restarts. */
    fun later() {
        val feed = status.offeredFeed ?: return
        status = UpdateStatus.Ready(feed)
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
                // A failed quiet check keeps a download it already has, and does not start the hourly limit.
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

/** The verified build the prompt offers: ready, or waiting for the install permission. */
val UpdateStatus.offeredFeed: UpdateFeed?
    get() = when (this) {
        is UpdateStatus.Ready -> feed
        is UpdateStatus.NeedsPermission -> feed
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
