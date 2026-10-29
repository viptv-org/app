package org.viptv.app.updates

sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Available(val feed: UpdateFeed, val required: Boolean) : UpdateDecision
}

// Never offers the same or a lower version_code: Android would refuse the downgrade anyway.
fun decideUpdate(feed: UpdateFeed, installedVersionCode: Long): UpdateDecision =
    if (feed.versionCode <= installedVersionCode) UpdateDecision.UpToDate
    else UpdateDecision.Available(feed, required = feed.minSupportedVersionCode?.let { installedVersionCode < it } == true)

const val UPDATE_CHECK_INTERVAL_MILLIS = 60L * 60 * 1000

fun updateCheckDue(lastCheckMillis: Long?, nowMillis: Long, playing: Boolean, manual: Boolean = false): Boolean =
    !playing && (manual || lastCheckMillis == null || nowMillis < lastCheckMillis ||
        nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL_MILLIS)

fun downloadMatches(feed: UpdateFeed, size: Long, sha256: String): Boolean =
    size == feed.size && sha256.equals(feed.sha256, ignoreCase = true)

fun downloadRetryDelayMillis(retry: Int): Long? = listOf(15_000L, 60_000L, 5 * 60_000L).getOrNull(retry - 1)

fun showUpdatePrompt(status: UpdateStatus, allowed: Boolean, snoozedVersionCodes: Set<Long>): Boolean {
    val feed = status.promptFeed ?: return false
    return allowed && feed.versionCode !in snoozedVersionCodes
}

fun showUpdatingScreen(status: UpdateStatus): Boolean = status is UpdateStatus.Installing || status is UpdateStatus.NeedsPermission

enum class UpdatingBack {
    Page,
    // The session is committing; Android cannot cancel it, so Back does nothing.
    Ignored,
    // The permission step has not started the session: Back is Later, as on the prompt.
    Later,
}

// Nothing behind the Updating screen may take the press.
fun updatingBack(status: UpdateStatus?): UpdatingBack = when (status) {
    is UpdateStatus.Installing -> UpdatingBack.Ignored
    is UpdateStatus.NeedsPermission -> UpdatingBack.Later
    else -> UpdatingBack.Page
}

fun backIgnoredWhileUpdating(status: UpdateStatus?): Boolean = updatingBack(status) == UpdatingBack.Ignored

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
