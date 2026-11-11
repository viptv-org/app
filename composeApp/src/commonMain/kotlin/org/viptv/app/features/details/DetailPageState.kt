package org.viptv.app.features.details

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import org.viptv.engine.wire.Card

private fun defaultDetailSection(episodes: Boolean, related: Boolean): DetailSection = when {
    episodes -> DetailSection.Episodes
    related -> DetailSection.Related
    else -> DetailSection.Information
}

@Composable
internal fun rememberDetailListState(card: Card?): LazyListState =
    rememberSaveable(card?.provider_id, card?.identity, saver = LazyListState.Saver) { LazyListState() }

@Composable
internal fun rememberDetailSection(card: Card?, episodes: Boolean, related: Boolean, information: Boolean): MutableState<DetailSection> =
    rememberSaveable(card?.provider_id, card?.identity, episodes, related, information) {
        mutableStateOf(defaultDetailSection(episodes, related))
    }
