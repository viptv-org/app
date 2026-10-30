package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.SettingsGroup
import org.viptv.app.features.settings.SettingsRow
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SettingsGroupsLayoutTest {
    @get:Rule val uiThread = UiThreadRule()

    private data class Layout(val name: String, val width: Int, val height: Int, val desktop: Boolean, val web: Boolean, val devices: Boolean, val stremio: Boolean,
        val rows: Map<SettingsGroup, List<SettingsRow>>)

    private val layouts = listOf(
        Layout("phone", 390, 1700, desktop = false, web = false, devices = true, stremio = true, rows = mapOf(
            SettingsGroup.Profile to listOf(SettingsRow.Profile, SettingsRow.LinkedServices, SettingsRow.ParentalControls, SettingsRow.ImportFromStremio),
            SettingsGroup.Playback to listOf(SettingsRow.Playback, SettingsRow.SubtitlesAudio),
            SettingsGroup.ThisDevice to listOf(SettingsRow.WatchOnTv, SettingsRow.About, SettingsRow.Help),
            SettingsGroup.Account to listOf(SettingsRow.Account, SettingsRow.HouseholdPin, SettingsRow.Devices, SettingsRow.AddDevice, SettingsRow.Addons, SettingsRow.SourceIntegrations, SettingsRow.SignOut))),
        Layout("tablet", 1000, 1200, desktop = false, web = false, devices = false, stremio = true, rows = mapOf(
            SettingsGroup.Profile to listOf(SettingsRow.Profile, SettingsRow.LinkedServices, SettingsRow.ParentalControls, SettingsRow.ImportFromStremio),
            SettingsGroup.Playback to listOf(SettingsRow.Playback, SettingsRow.SubtitlesAudio),
            SettingsGroup.ThisDevice to listOf(SettingsRow.WatchOnTv, SettingsRow.About, SettingsRow.Help),
            SettingsGroup.Account to listOf(SettingsRow.Account, SettingsRow.HouseholdPin, SettingsRow.AddDevice, SettingsRow.Addons, SettingsRow.SourceIntegrations, SettingsRow.SignOut))),
        Layout("desktop", 1440, 1200, desktop = true, web = false, devices = false, stremio = false, rows = mapOf(
            SettingsGroup.Profile to listOf(SettingsRow.Profile, SettingsRow.LinkedServices, SettingsRow.ParentalControls),
            SettingsGroup.Playback to listOf(SettingsRow.Playback, SettingsRow.SubtitlesAudio),
            SettingsGroup.ThisDevice to listOf(SettingsRow.About, SettingsRow.Help),
            SettingsGroup.Account to listOf(SettingsRow.Account, SettingsRow.HouseholdPin, SettingsRow.AddDevice, SettingsRow.Addons, SettingsRow.SourceIntegrations, SettingsRow.SignOut))),
        Layout("web", 1440, 1200, desktop = false, web = true, devices = true, stremio = true, rows = mapOf(
            SettingsGroup.Profile to listOf(SettingsRow.Profile, SettingsRow.LinkedServices, SettingsRow.ParentalControls, SettingsRow.ImportFromStremio),
            SettingsGroup.Playback to listOf(SettingsRow.Playback, SettingsRow.SubtitlesAudio),
            SettingsGroup.ThisDevice to listOf(SettingsRow.About, SettingsRow.Help),
            SettingsGroup.Account to listOf(SettingsRow.Account, SettingsRow.HouseholdPin, SettingsRow.Devices, SettingsRow.AddDevice, SettingsRow.Addons, SettingsRow.SourceIntegrations, SettingsRow.SignOut))),
    )

    @Test fun everyLayoutShowsTheFourGroupsInOrderWithItsOwnRows() {
        for (layout in layouts) {
            val scene = scene(layout)
            try {
                val nodes = render(scene)
                val heading = nodes.filter { it.config.getOrNull(SemanticsProperties.Heading) != null }
                assertEquals(SettingsGroup.entries.map { it.label.uppercase() }, heading.map(::text),
                    "${layout.name}: four labelled groups in order, as headings")
                val groupTop = heading.associate { text(it) to it.boundsInRoot.top }
                val clickable = nodes.filter { it.config.contains(SemanticsActions.OnClick) }
                for (group in SettingsGroup.entries) {
                    val start = requireNotNull(groupTop[group.label.uppercase()])
                    val end = SettingsGroup.entries.dropWhile { it != group }.drop(1).firstOrNull()?.let { groupTop[it.label.uppercase()] } ?: Float.MAX_VALUE
                    val held = clickable.filter { it.boundsInRoot.top in start..end }.mapNotNull(::rowOf)
                    assertEquals(layout.rows.getValue(group), held, "${layout.name}: the ${group.label} group holds its rows in order")
                }
                for (row in SettingsRow.entries) {
                    if (layout.rows.values.any { row in it }) continue
                    assertTrue(clickable.none { rowOf(it) == row }, "${layout.name}: ${row.label} has no page here and must be absent")
                }
                if (layout.web) assertTrue(nodes.any { text(it) == "Settings" }, "the web rail is titled Settings")
            } finally { scene.close() }
        }
    }

    @Test fun signOutIsTheLastRowOfTheAccountGroup() {
        val scene = scene(layouts.first())
        try {
            val rows = render(scene).filter { it.config.contains(SemanticsActions.OnClick) }
                .sortedBy { it.boundsInRoot.top }.mapNotNull(::rowOf)
            assertEquals(SettingsRow.SignOut, rows.last(), "Sign out is the last row")
            assertTrue(rows.indexOf(SettingsRow.SignOut) > rows.indexOf(SettingsRow.Addons), "Sign out follows Add-ons")
        } finally { scene.close() }
    }

    private fun scene(layout: Layout) = ImageComposeScene(layout.width, layout.height, Density(1f)) {
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                profile = Profile("1", "Viewer", null),
                account = { Text("account page") },
                parental = { Text("parental page") },
                preferences = { Text("preferences page") },
                sectionPreferences = { _, _ -> Text("preferences page") },
                stremioImport = if (layout.stremio) ({ _, _, _ -> }) else null,
                addDevice = { Text("add device page") },
                householdPin = { Text("household pin page") },
                sourceIntegrations = { _, _, _ -> Text("source integrations page") },
                devices = if (layout.devices) ({ _, _ -> }) else null,
                linkedServices = { _ -> Text("linked services page") },
                desktop = layout.desktop)
        } }
    }

    private fun render(scene: ImageComposeScene): List<SemanticsNode> {
        var frame = 0L
        repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        return scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    }

    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    // A row's node carries its label plus any note, so the label is a prefix of the merged text.
    private fun rowOf(node: SemanticsNode): SettingsRow? {
        val label = text(node)
        return SettingsRow.entries.firstOrNull { label == it.label || label.startsWith("${it.label} ") }
    }
}
