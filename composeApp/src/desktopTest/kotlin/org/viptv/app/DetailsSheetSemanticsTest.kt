package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.details.PhoneDetailHero
import org.viptv.app.features.home.CardPreview
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class)
class DetailsSheetSemanticsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val card = Card(MediaIdentity("tt797", "movie", emptyList()), provider_id = "addon:4",
        title = "Details Sheet Title", badges = emptyList())

    @Test fun thePhoneDetailsSheetNamesPlayMyListAndMoreInfo() {
        val scene = scene {
            CardPreview(card, saved = false, onPlay = {}, onSave = {}, onInfo = {}, onHoverChanged = {})
        }
        try {
            frames(scene)
            assertNamed(scene, "Play", "Play")
            assertNamed(scene, "My List", "Add to My List")
            assertNamed(scene, "More info", "More info")
        } finally { scene.close() }
    }

    @Test fun aSavedTitleOnThePhoneDetailsSheetNamesTheMyListActionAsRemoving() {
        val scene = scene {
            CardPreview(card, saved = true, onPlay = {}, onSave = {}, onInfo = {}, onHoverChanged = {})
        }
        try {
            frames(scene)
            assertNamed(scene, "My List", "Remove from My List")
        } finally { scene.close() }
    }

    @Test fun thePhoneDetailsHeroNamesItsMyListAction() {
        val unsaved = scene { PhoneDetailHero(card, play = {}, saved = false) }
        try {
            frames(unsaved)
            assertNamed(unsaved, "My List", "Add to My List")
        } finally { unsaved.close() }

        val saved = scene { PhoneDetailHero(card, play = {}, saved = true) }
        try {
            frames(saved)
            assertNamed(saved, "My List", "Remove from My List")
        } finally { saved.close() }
    }

    private fun scene(content: @Composable () -> Unit) = ImageComposeScene(390, 844, Density(1f)) {
        ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        }
    }

    private fun assertNamed(scene: ImageComposeScene, action: String, expected: String) {
        val matches = nodes(scene).filter {
            it.config.contains(SemanticsActions.OnClick) &&
                it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains(expected)
        }
        if (matches.size != 1) {
            val names = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                .map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(", ") }
            fail("$action is announced as \"$expected\"; clickable names were $names")
        }
        assertEquals(expected, matches.single().config.getOrNull(SemanticsProperties.ContentDescription)?.single(),
            "$action reads \"$expected\"")
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun frames(scene: ImageComposeScene, count: Int = 20) =
        repeat(count) { scene.render(it * 16_666_667L).close() }

    private companion object {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    }
}
