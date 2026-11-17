package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsEpisodeIntentUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val show = Card(MediaIdentity("show", "series", emptyList()), provider_id = "catalog", title = "Show", badges = emptyList())
    private val episode = show.copy(identity = MediaIdentity("show:1:1", "series", emptyList(), EpisodeCoordinates("aired", 1, 1)),
        parent_identity = show.identity, title = "Episode one", released_at_millis = 1_700_000_000_000)

    @Test fun everyDetailsLayoutKeepsEpisodeChoiceExplicitAndHeroPlayAutomatic() {
        for (variant in listOf("phone", "desktop", "tablet", "browser", "tv")) {
            val plays = mutableListOf<Card>()
            val asks = mutableListOf<Card>()
            val tv = variant == "tv"
            val scene = ImageComposeScene(if (variant == "phone") 390 else if (tv) 1920 else 1440, 1600, Density(1f)) {
                ViptvTheme(tv) { DetailsScreen(DetailSnapshot(show, episodes = listOf(episode)), false, null, {},
                    { plays += it }, television = tv, tablet = variant == "tablet", browser = variant == "browser",
                    chooseSource = { asks += it }) }
            }
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(false) }
            fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ") + node.children.joinToString(" ") { text(it) }
            fun episodeRow() = nodes().lastOrNull { it.config.contains(SemanticsActions.OnClick) && text(it).contains(episode.title) && !text(it).contains("options", true) }
            try {
                scene.renderUntil(what = "$variant episode choice") { episodeRow() != null }
                assertTrue(episodeRow()!!.config[SemanticsActions.OnClick].action!!.invoke(), variant)
                assertEquals(listOf(episode), asks, variant)
                assertTrue(plays.isEmpty(), variant)
                val play = nodes().last { it.config.contains(SemanticsActions.OnClick) && text(it).trim().startsWith("Play") }
                assertTrue(play.config[SemanticsActions.OnClick].action!!.invoke(), variant)
                assertEquals(1, plays.size, variant)
                assertEquals(listOf(episode), asks, variant)
            } finally { scene.close() }
        }
    }
}
