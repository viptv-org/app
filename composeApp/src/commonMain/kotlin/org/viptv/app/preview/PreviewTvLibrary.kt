package org.viptv.app.preview

import org.viptv.app.resources.Res
import org.viptv.engine.wire.*

internal object PreviewTvLibrary {
    private val titles = listOf("The End of Oak Street", "The Muppet Show", "Project Hail Mary", "The Whisper Man",
        "The Mandalorian and Grogu", "The Invite", "Weapons", "Mayday", "Obsession", "Pressure",
        "Those Who Wish Me Dead", "Practical Magic", "Tony", "Backrooms")
    private val artwork = listOf("tv-movie-5.jpg", "muppets.jpg", "tv-movie-0.jpg", "tv-movie-3.jpg",
        "mandalorian-grogu.jpg", "tv-movie-4.jpg", "tv-movie-2.jpg", "tv-movie-1.jpg", "tv-movie-13.jpg",
        "tv-movie-6.jpg", "tv-movie-8.jpg", "tv-movie-10.jpg", "tv-movie-12.jpg", "tv-movie-9.jpg")
    val cards = titles.mapIndexed { index, title ->
        Card(MediaIdentity("tv-list-$index", if (index == 1) "series" else "movie", emptyList()), "preview", title,
            subtitle = if (index == 0) "2026 · Action" else null,
            poster = Res.getUri("files/ui-preview/${artwork[index]}"),
            badges = if (index == 1) listOf("New episode") else emptyList())
    }
    val watched = cards.mapIndexed { index, card -> card.identity.id to (cards.size - index).toLong() }.toMap()
}
