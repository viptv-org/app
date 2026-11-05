package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Rule
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.PendingMutationUi
import org.viptv.app.components.saveActionLabel
import org.viptv.app.features.details.DesktopDetailHero
import org.viptv.app.features.details.PhoneDetailHero
import org.viptv.app.features.details.TvDetailExtras
import org.viptv.app.features.details.TvDetailHero
import org.viptv.app.features.home.DesktopHero
import org.viptv.app.features.home.TabletHero
import org.viptv.app.features.home.TvHero
import org.viptv.app.state.PendingMutations
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SaveActionNameTest {
    @get:Rule val uiThread = UiThreadRule()

    private val card = Card(MediaIdentity("tt812", "movie", emptyList()), provider_id = "addon:4",
        title = "Save Action Title", badges = emptyList())

    private class Hero(val name: String, val width: Int, val height: Int, val television: Boolean = false,
        val content: @Composable (saved: Boolean) -> Unit)

    private val heroes = listOf(
        Hero("phone details", 390, 844) { saved -> PhoneDetailHero(card, play = {}, save = {}, saved = saved) },
        Hero("desktop details", 1440, 1000) { saved -> DesktopDetailHero(card, play = {}, save = {}, saved = saved) },
        Hero("tv details", 1920, 1080, television = true) { saved ->
            TvDetailHero(card, TvDetailExtras(), 1f, play = {}, save = {}, trailer = {}, restart = {},
                first = FocusRequester(), down = null, requestInitialFocus = false, saved = saved)
        },
        Hero("desktop home", 1440, 1000) { saved -> DesktopHero(card, {}, {}, save = {}, saved = saved) },
        Hero("tablet home", 1024, 900) { saved -> TabletHero(card, {}, {}, {}, {}, saved) },
        Hero("tv home", 1920, 1080, television = true) { saved -> TvHero(card, {}, {}, save = {}, saved = saved) },
    )

    @Test fun everySaveControlKeepsItsNameWhileTheMutationIsPending() {
        for (hero in heroes) for (saved in listOf(false, true)) {
            val state = "${hero.name} ${if (saved) "saved" else "unsaved"}"
            val expected = saveActionLabel(saved)
            val idle = names(hero, saved, pending = false)
            assertEquals(1, idle.count { it == listOf(expected) }, "$state names its save control $expected")
            assertEquals(idle, names(hero, saved, pending = true), "$state keeps every name while saving")
        }
    }

    @Test fun noSaveIconCarriesTheNameThePendingStateReplaces() {
        val root = File("src/commonMain/kotlin/org/viptv/app")
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            Regex("""Lucide\.Plus,\s*(if \(|saveActionLabel)""").findAll(file.readText()).map { "${file.name}: ${it.value.trim()}" }
        }.toList()
        assertEquals(emptyList(), found, "name the control with Modifier.saveActionName, not the icon the spinner replaces")
    }

    private fun scene(hero: Hero, saved: Boolean, pending: Boolean): ImageComposeScene {
        val mutations = if (pending) PendingMutations().starting(saveTarget(card)) else PendingMutations()
        return ImageComposeScene(hero.width, hero.height, Density(1f)) {
            CompositionLocalProvider(LocalPendingMutation provides PendingMutationUi(pending = mutations)) {
                ViptvTheme(television = hero.television, pointer = !hero.television) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) { hero.content(saved) }
                }
            }
        }
    }

    private fun names(hero: Hero, saved: Boolean, pending: Boolean): List<List<String>> {
        val scene = scene(hero, saved, pending)
        return try {
            frames(scene)
            scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .filter { it.config.contains(SemanticsActions.OnClick) }
                .map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                .sortedBy { it.joinToString() }
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, count: Int = 20) =
        repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
}
