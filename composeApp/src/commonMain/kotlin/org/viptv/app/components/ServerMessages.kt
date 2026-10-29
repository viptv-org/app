package org.viptv.app.components

internal object ServerMessages {

    const val ROW_FAILURE = "The provider didn’t answer in time."

    const val LIST_REASON = "Your list could not be updated."

    const val LIVE_REASON = "The channel list could not be updated."

    const val PARENT_ROW = "This provider could not answer. Try again."

    const val PARENT_TITLE = "This title cannot be approved through this integration."

    const val EMAIL_STATUS = "Sent to your email address."

    const val GENERIC = "Something went wrong. Try again."

    private const val MAX_LENGTH = 140

    private val transport = Regex(
        "\\b(api|route|routes|endpoint|json|http|https|payload|backend|database|sql|" +
            "exception|traceback|panic|undefined|null|nil|stack)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val identifier = Regex("[{}<>=\"\\\\_|`]|::|://")

    fun human(message: String?, fallback: String): String =
        message?.trim()?.takeIf { isHumanCopy(it) } ?: fallback

    private fun isHumanCopy(text: String): Boolean = text.isNotEmpty() &&
        text.length <= MAX_LENGTH &&
        text.first().isLetter() &&
        text.any { it.isWhitespace() } &&
        text.any { it.isLowerCase() } &&
        !transport.containsMatchIn(text) &&
        !identifier.containsMatchIn(text)
}
