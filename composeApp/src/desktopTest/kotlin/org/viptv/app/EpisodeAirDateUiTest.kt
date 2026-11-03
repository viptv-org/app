package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.details.DesktopEpisodeRow
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.app.features.details.episodeUpcoming
import org.viptv.app.features.details.episodeUpcomingText
import org.viptv.app.features.details.lastReleasedOrder
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import java.util.TimeZone
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EpisodeAirDateUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val now = 1_772_668_800_000L
    private val released = 1_772_582_400_000L
    private val upcoming = 1_773_187_200_000L

    private fun episode(number: Long, releasedAt: Long? = null) = Card(
        MediaIdentity("show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        provider_id = "addon:4", title = "Episode $number", badges = emptyList(), released_at_millis = releasedAt)

    private val episodes = listOf(episode(1), episode(2, released), episode(3, upcoming), episode(4))

    @Test fun releasedUpcomingAndNoDateRowsAreClassified() {
        val last = lastReleasedOrder(episodes, now)
        assertFalse(episodeUpcoming(episodes[0], last, now), "a no-date episode before the last release is not upcoming")
        assertFalse(episodeUpcoming(episodes[1], last, now), "a released episode is not upcoming")
        assertTrue(episodeUpcoming(episodes[2], last, now), "a future date is upcoming")
        assertTrue(episodeUpcoming(episodes[3], last, now), "a no-date episode after the last release is upcoming")
        assertNull(episodes[0].released_at_millis)
        assertEquals("Upcoming", episodes[3].episodeUpcomingText())
        assertTrue(episodes[2].episodeUpcomingText().startsWith("Upcoming · Airs "))
    }

    private fun frames(scene: ImageComposeScene) {
        repeat(4) { scene.render(it * 16_666_667L).close() }
        scene.render(100_000_000L).close()
    }

    private fun texts(scene: ImageComposeScene): List<String> = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(false) }
        .flatMap { node: SemanticsNode -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    @Test fun episodeRowsShowReleaseDateUpcomingAndKeepNoDateRowsSilent() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val last = lastReleasedOrder(episodes, now)
            val phone = ImageComposeScene(390, 760, Density(1f)) { ViptvTheme {
                Column(Modifier.fillMaxSize().background(Palette.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    episodes.forEach { DetailEpisodeCard(it, true, {}, Modifier.fillMaxWidth(), comingUp = episodeUpcoming(it, last, now)) }
                }
            } }
            try { frames(phone); assertRowLabels(texts(phone), "phone") } finally { phone.close() }

            val desktop = ImageComposeScene(1000, 700, Density(1f)) { ViptvTheme {
                Column(Modifier.fillMaxSize().background(Palette.background)) {
                    episodes.forEach { DesktopEpisodeRow(it, {}, comingUp = episodeUpcoming(it, last, now)) }
                }
            } }
            try { frames(desktop); assertRowLabels(texts(desktop), "desktop") } finally { desktop.close() }

            val tv = ImageComposeScene(1920, 520, Density(1f)) { ViptvTheme(true) {
                Row(Modifier.fillMaxSize().background(Palette.background).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    episodes.forEach { TvEpisodeCard(it, 0.95f, {}, {}, null, FocusRequester(), comingUp = episodeUpcoming(it, last, now)) }
                }
            } }
            try { frames(tv); assertRowLabels(texts(tv), "tv") } finally { tv.close() }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun assertRowLabels(all: List<String>, platform: String) {
        assertEquals(1, all.count { it == "Mar 4, 2026" }, "$platform: released rows show the date: $all")
        assertEquals(1, all.count { it == "Upcoming · Airs Mar 11" }, "$platform: upcoming rows name the air date: $all")
        assertEquals(1, all.count { it == "Upcoming" }, "$platform: no-date upcoming rows still say upcoming: $all")
        assertTrue(all.none { it == "Mar 11, 2026" || it.contains("Airs Mar 11,") }, "$platform: the upcoming date never leaks as a release: $all")
    }
}