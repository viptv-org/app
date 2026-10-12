package org.viptv.app.preview

import org.viptv.app.resources.Res

internal object PreviewTvLive {
    private val newsFrame = Res.getUri("files/ui-preview/tv-live.jpg")
    val categories = listOf("News", "Sports", "Movies", "Kids", "Music")
    val channels = PreviewLive.channels.mapIndexed { index, channel ->
        if (index == 0) channel.copy(card = channel.card.copy(landscape = newsFrame, badges = listOf("HD", "CC")),
            programs = channel.programs.map { it.copy(artwork = newsFrame) }) else channel
    }
}
