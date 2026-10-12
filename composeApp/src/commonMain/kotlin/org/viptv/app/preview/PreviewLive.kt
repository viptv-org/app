package org.viptv.app.preview

import org.viptv.app.features.live.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

internal object PreviewLive {
    const val start = 18 * 60
    const val now = start + 12
    private val titles = listOf(
        listOf("[Evening News]", "[Quiz Night]", "[Drama: Season Premiere]"),
        listOf("[World News Tonight]", "[Local Report]", "[Game Show]", "[Movie: The Heist]"),
        listOf("[Movie: The Long Night]", "[Sitcom Double Bill]", "[News at 8]"),
        listOf("[Football Pre-game]", "[Football: Live]"),
        listOf("[Evening News]", "[Entertainment Tonight]", "[Crime Drama]"),
        listOf("[Nature Hour]", "[Antiques Show]", "[Travel]"),
    )
    val channels = listOf("WNBC", "WABC", "WPIX", "WNYW", "WCBS", "WLIW").mapIndexed { index, name ->
        val programs = titles[index].mapIndexed { part, title ->
            val begin = when (index) {
                2 -> listOf(start - 30, start + 60, start + 120)[part]
                3 -> listOf(start, start + 60)[part]
                5 -> listOf(start, start + 60, start + 120)[part]
                else -> start + part * 30
            }
            val end = if (part == titles[index].lastIndex) start + 150 else when (index) {
                2, 3, 5 -> begin + if (index == 2 && part == 0) 90 else 60
                else -> begin + 30
            }
            LiveProgram("$name-$part", title, begin, end, "[Local and national headlines, weather and sports.]", PreviewData.posters[6].poster)
        }
        LiveChannel(Card(MediaIdentity("live-$name", "channel", emptyList()), "preview", name,
            landscape = PreviewData.posters[6].poster, badges = emptyList()),
            listOf("4", "7", "11", "5", "2", "21")[index],
            listOf("News", "News", "Movies", "Sports", "News", "Kids")[index], programs)
    }
}
