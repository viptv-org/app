package org.viptv.app.features.continuewatching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.engine.wire.Card

/**
 * The Continue Watching removal a page offers its cards: one menu entry, one confirmation, one request.
 * A page that shows Continue Watching cards outside its own shelf provides [offer] to those cards and draws [confirmation].
 */
@Stable
internal class ContinueWatchingRemoval(private val remove: ((Card) -> Unit)?) {
    var confirming by mutableStateOf<Card?>(null)
        private set
    val offer = remove?.let { ListRemoval(REMOVE_CONTINUE_LABEL, continueWatching = true) { card -> confirming = card } }

    fun confirm() {
        val card = confirming ?: return
        confirming = null
        remove?.invoke(card)
    }

    fun dismiss() { confirming = null }

    @Composable
    fun confirmation() {
        confirming?.let { card ->
            ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = ::confirm, dismiss = ::dismiss)
        }
    }
}