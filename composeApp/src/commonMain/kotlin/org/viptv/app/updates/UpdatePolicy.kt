package org.viptv.app.updates

/** What a fetched feed means for the installed build. */
sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    /** [required]: the installed build is below the feed's `min_supported_version_code`. */
    data class Available(val feed: UpdateFeed, val required: Boolean) : UpdateDecision
}

/** Never offers the same or a lower version_code: Android would refuse the downgrade anyway. */
fun decideUpdate(feed: UpdateFeed, installedVersionCode: Long): UpdateDecision =
    if (feed.versionCode <= installedVersionCode) UpdateDecision.UpToDate
    else UpdateDecision.Available(feed, required = feed.minSupportedVersionCode?.let { installedVersionCode < it } == true)

const val UPDATE_CHECK_INTERVAL_MILLIS = 60L * 60 * 1000

/**
 * Background checks run at most once an hour and never while the player is open. "Check for
 * updates" in Settings skips the hourly limit. A clock that moved backwards allows a check.
 */
fun updateCheckDue(lastCheckMillis: Long?, nowMillis: Long, playing: Boolean, manual: Boolean = false): Boolean =
    !playing && (manual || lastCheckMillis == null || nowMillis < lastCheckMillis ||
        nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL_MILLIS)

/** A download is offered only when both its size and SHA-256 match the feed. */
fun downloadMatches(feed: UpdateFeed, size: Long, sha256: String): Boolean =
    size == feed.size && sha256.equals(feed.sha256, ignoreCase = true)

/** Waits before download retries `1, 2, 3`; null once retries are exhausted (the next check starts over). */
fun downloadRetryDelayMillis(retry: Int): Long? = listOf(15_000L, 60_000L, 5 * 60_000L).getOrNull(retry - 1)

/**
 * The "Update ready" prompt shows outside the player, for a verified download (or one waiting for
 * the install permission) the viewer has not put off with Later this session.
 */
fun showUpdatePrompt(status: UpdateStatus, playing: Boolean, snoozedVersionCodes: Set<Long>): Boolean {
    if (playing) return false
    val feed = when (status) {
        is UpdateStatus.Ready -> status.feed
        is UpdateStatus.NeedsPermission -> status.feed
        else -> return false
    }
    return feed.versionCode !in snoozedVersionCodes
}

/** "Checked just now", "Checked 5 min ago", ... for Settings › About. */
fun lastCheckLabel(lastCheckMillis: Long?, nowMillis: Long): String {
    if (lastCheckMillis == null) return "Not checked yet"
    val minutes = ((nowMillis - lastCheckMillis).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "Checked just now"
        minutes < 60 -> "Checked $minutes min ago"
        minutes < 48 * 60 -> "Checked ${minutes / 60} h ago"
        else -> "Checked ${minutes / (24 * 60)} days ago"
    }
}
