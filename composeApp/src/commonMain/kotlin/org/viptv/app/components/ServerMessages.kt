package org.viptv.app.components

/**
 * NAV-06: server-carried text never reaches the UI verbatim.
 *
 * The backend sends free-form `message`/`reason` fields (Home rows, parental
 * title search, library and live availability) and a failed call can carry
 * server internals ("API route not found"). Every surface passes the decoded
 * text through [human] together with the sentence it shows instead, so a
 * server change can never rewrite a status line or a toast.
 *
 * The test is deliberately narrow: text survives only when it reads like a
 * sentence a person wrote — starts with a letter, contains a space, has a
 * lower-case character, is at most a paragraph long, and carries no transport
 * vocabulary or identifier characters.
 */
internal object ServerMessages {

    /** Board Tv3RowStates: one Home row failed, the rest of Home is fine. */
    const val ROW_FAILURE = "The provider didn’t answer in time."

    /** Board M3MyList / Tv3MyList: the list loaded but its last read failed. */
    const val LIST_REASON = "Your list could not be updated."

    /** Board VIPTV Live: the channel list or its guide could not load. */
    const val LIVE_REASON = "The channel list could not be updated."

    /** Parental search: one provider did not answer. */
    const val PARENT_ROW = "This provider could not answer. Try again."

    /** Parental search: this integration cannot approve the title. */
    const val PARENT_TITLE = "This title cannot be approved through this integration."

    /** Board D3SignInEmail: email-code screen subtitle. */
    const val EMAIL_STATUS = "Sent to your email address."

    /** Last resort for a surface with no board sentence of its own. */
    const val GENERIC = "Something went wrong. Try again."

    private const val MAX_LENGTH = 140

    private val transport = Regex(
        "\\b(api|route|routes|endpoint|json|http|https|payload|backend|database|sql|" +
            "exception|traceback|panic|undefined|null|nil|stack)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val identifier = Regex("[{}<>=\"\\\\_|`]|::|://")

    /** Server text when it is human copy, [fallback] (board sentence) otherwise. */
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
