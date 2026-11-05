package org.viptv.app.connection

import org.viptv.engine.wire.EpisodeCoordinates

internal fun Long?.seasonNumber(): Long = this ?: 1L

internal fun EpisodeCoordinates.seasonNumber(): Long = season.seasonNumber()