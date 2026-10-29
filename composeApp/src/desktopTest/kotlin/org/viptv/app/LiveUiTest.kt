package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun channel() = LiveChannel(Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()), id = "one", connectionId = "m3u:1")
    @Test fun tvFavoriteAndCategoryAreRealActionsWithinTheExistingLayout() {
        var saved = 0; var filtered = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LiveScreen(listOf(channel()), true, null, null, {}, {}, favoriteChanged = { saved++ }, favoritesFilter = { filtered++ })
            } }
        }
        try {
            frames(scene)
            val favorite = clickable(scene, "Favourite channel")
            assertTrue(favorite.boundsInRoot.bottom < 460f, "Favorite stays beside the preview, above guide rows")
            assertTrue(favorite.config[SemanticsActions.OnClick].action!!.invoke()); assertEquals(1, saved)
            assertTrue(clickable(scene, "Favourites").config[SemanticsActions.OnClick].action!!.invoke()); assertEquals(1, filtered)
            capture(scene, "tv-live-favorites")
        } finally { scene.close() }
    }

    @Test fun sourceSelectorIsConditionalAndNamesTheSelectedAuthorizedFeed() {
        for (sources in listOf(mapOf("m3u:1" to "Playlist"), mapOf("m3u:1" to "Playlist", "xtream:2" to "Other feed"))) {
            val scene = ImageComposeScene(1200, 800, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(listOf(channel()), false, null, null, {}, {}, sources = sources, selectedSource = "m3u:1", sourceChanged = {})
                } }
            }
            try {
                frames(scene)
                val labels = nodes(scene).filter { text(it).startsWith("Source:") }
                assertEquals(sources.size > 1, labels.isNotEmpty())
                if (sources.size > 1) assertTrue(labels.any { text(it) == "Source: Playlist" })
            } finally { scene.close() }
        }
    }

    @Test fun emptyFavoritesStateExplicitlyNamesTheSelectedFeedOnDesktopAndTv() {
        for (tv in listOf(false, true)) {
            val scene = ImageComposeScene(if (tv) 1920 else 1200, if (tv) 1080 else 800, Density(1f)) {
                ViptvTheme(tv) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(listOf(channel()), tv, null, null, {}, {}, favoritesOnly = true, favoritesFilter = {},
                        sources = mapOf("m3u:1" to "Playlist", "xtream:2" to "Other feed"), selectedSource = "xtream:2", sourceChanged = {})
                } }
            }
            try { frames(scene); assertTrue(nodes(scene).any { text(it) == "No favourite channels from Other feed." }) }
            finally { scene.close() }
        }
    }
    @Test fun groupAndFavouritesAreChipsThatApplyAtOnceAndClear() {
        for (tv in listOf(false, true)) {
            val groups = mutableListOf<String?>()
            var favorites = 0
            val channels = listOf(channel(), LiveChannel(Card(MediaIdentity("two", "live", emptyList()), provider_id = "m3u:1",
                title = "Fixture sport", badges = emptyList()), category = "Sport", groupId = "sport"))
            val scene = ImageComposeScene(if (tv) 1920 else 1200, if (tv) 1080 else 800, Density(1f)) {
                ViptvTheme(tv) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(channels, tv, null, null, {}, {}, groupLabels = mapOf("news" to "News", "sport" to "Sport"),
                        groupChanged = { groups += it }, favoritesFilter = { favorites++ })
                } }
            }
            try {
                frames(scene)
                val layout = if (tv) "TV" else "desktop"
                assertTrue(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == "Group: All" }, "$layout: the group is a chip")
                assertFalse(nodes(scene).any { text(it) == "Clear" }, "$layout: nothing to clear yet")
                open(scene, "Group: All")
                pick(scene, "Sport")
                assertEquals(listOf<String?>("sport"), groups, "$layout: a group pick applies at once")
                assertTrue(clickable(scene, "Clear").config[SemanticsActions.OnClick].action!!.invoke())
                frames(scene)
                assertEquals(listOf<String?>("sport", null), groups, "$layout: Clear drops the group")
                assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Role) == Role.Button && text(it) == "Clear" })
                assertTrue(clickable(scene, "Favourites").config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, favorites, "$layout: Favourites is a toggle chip")
            } finally { scene.close() }
        }
    }
    private fun open(scene: ImageComposeScene, label: String) {
        val trigger = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == label }
        assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke()); frames(scene)
    }
    private fun pick(scene: ImageComposeScene, label: String) {
        val row = nodes(scene).single { it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) && text(it).startsWith(label) }
        assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke()); frames(scene)
    }
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun clickable(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun capture(scene: ImageComposeScene, name: String) {
        val directory = System.getenv("VIPTV_LIVE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
}
