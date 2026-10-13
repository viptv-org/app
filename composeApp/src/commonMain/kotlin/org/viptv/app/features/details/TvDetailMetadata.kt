package org.viptv.app.features.details

import androidx.compose.runtime.Composable
import org.viptv.app.components.TvMediaMetadata
import org.viptv.engine.wire.Card

@Composable
internal fun TvDetailMetadata(card: Card, scale: Float) { TvMediaMetadata(card, scale) }
