package org.viptv.app.features.remote

import org.viptv.app.components.phoneDestinations
import org.viptv.app.state.Screen

internal object RemoteHeaderPages {
    private val screens = listOf(Screen.Home, Screen.Live, Screen.Library)

    fun shows(screen: Screen) = screen in screens

    val names: String = screens.map { page -> phoneDestinations.first { it.screen == page }.label }
        .let { labels -> labels.dropLast(1).joinToString(", ") + " and " + labels.last() }
}