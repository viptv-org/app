package org.viptv.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.viptv.app.features.details.BrowserDetailsDialog
import org.viptv.app.features.entry.MobilePinScreen
import org.viptv.app.features.entry.TvPinScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.components.LocalSelectPresentation
import org.viptv.app.components.PhoneSheet
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.SelectPresentation
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.preview.PreviewData

internal class A11ySurface(
    val name: String,
    val width: Int,
    val height: Int,
    val television: Boolean = false,
    val content: @Composable () -> Unit,
)

internal val a11ySurfaces: List<A11ySurface> = listOf(
    A11ySurface("Player, phone", 390, 844) {
        PlayerScreen(null, true, null, {}, {}, {}, title = PreviewData.playerTitle, phone = true,
            preview = PlayerFact.Progress(842_000, 3_132_000))
    },
    A11ySurface("Player, desktop", 1280, 720) {
        PlayerScreen(null, false, null, {}, {}, {}, title = PreviewData.playerTitle, phone = false,
            preview = PlayerFact.Progress(842_000, 3_132_000))
    },
    A11ySurface("Profile PIN, phone", 390, 844) {
        MobilePinScreen(a11yLockedProfile, "12", false, null, {}, {}, {}, {})
    },
    A11ySurface("Profile PIN, TV", 1920, 1080, television = true) {
        TvPinScreen(a11yLockedProfile, "12", false, null, {}, {}, {}, {})
    },
    A11ySurface("Sheet, phone", 390, 844) {
        PhoneSheet(visible = true, title = "Seasons", dismiss = {}, touch = true) { Text("content") }
    },
    A11ySurface("Filter sheet, phone", 390, 844) {
        val open = remember { mutableStateOf(true) }
        CompositionLocalProvider(LocalSelectPresentation provides SelectPresentation.Sheet) {
            SearchableSelect("Type", listOf(SelectOption("series", "TV")), listOf("series"), {}, open = open)
        }
    },
    A11ySurface("Filter panel, TV", 1920, 1080, television = true) {
        val open = remember { mutableStateOf(true) }
        CompositionLocalProvider(LocalSelectPresentation provides SelectPresentation.Panel) {
            SearchableSelect("Type", listOf(SelectOption("series", "TV")), listOf("series"), {}, open = open)
        }
    },
    A11ySurface("Details dialog, desktop", 1280, 900) {
        BrowserDetailsDialog(PreviewData.detail, false, null, {}, {})
    },
    A11ySurface("Source panel, tablet", 1024, 768) {
        SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, {}, PreviewData.detail, tablet = true)
    },
    A11ySurface("Source panel, TV", 1920, 1080, television = true) {
        SourcesScreen(PreviewData.hero, PreviewData.sources, false, null, {}, {}, PreviewData.detail, television = true)
    },
)