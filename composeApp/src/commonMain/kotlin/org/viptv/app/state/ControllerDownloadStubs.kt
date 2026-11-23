package org.viptv.app.state

import org.viptv.engine.wire.Card

fun ApplicationController.downloadTitle(card: Card) = Unit

fun ApplicationController.downloadSeason(series: Card, season: Long) = Unit

fun ApplicationController.downloadNext(series: Card, count: Int) = Unit

fun ApplicationController.downloadSeries(series: Card) = Unit

fun ApplicationController.playDownload(id: String) = Unit

internal val ApplicationState.offline: Boolean get() = false
