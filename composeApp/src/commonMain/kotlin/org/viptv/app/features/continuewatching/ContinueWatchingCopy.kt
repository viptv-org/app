package org.viptv.app.features.continuewatching

// copy.md ## Empty states — Continue Watching.
internal const val CONTINUE_EMPTY_TITLE = "Nothing in progress"
internal const val CONTINUE_EMPTY_MESSAGE = "Titles you start watching appear here."
internal const val CONTINUE_EMPTY_ACTION = "Browse Home"
internal const val CONTINUE_FAILED_TITLE = "Couldn’t load Continue Watching"
internal const val CONTINUE_FAILED_FALLBACK = "Check the connection and try again."
internal const val CONTINUE_RETRY_ACTION = "Try again"

internal fun continueWatchingFailureMessage(error: String?): String =
    error?.takeIf { it.isNotBlank() } ?: CONTINUE_FAILED_FALLBACK