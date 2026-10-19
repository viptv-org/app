package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusColumn
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette

/**
 * TVUI-07: the board's About page. Every row opens content that exists in this
 * build — the build identity, the connection in use, the exact list a support
 * report would contain (this build has no report endpoint, so none is faked),
 * the app's data statement and the licence files bundled under
 * composeResources/files/licenses.
 */
internal const val VIPTV_VERSION = "0.1.0-experiment"

private enum class TvAboutPanel { Build, Connection, Diagnostics, Privacy, Licences }

private data class AboutLicence(val file: String, val title: String)
private data class AboutNotice(val title: String, val firstLine: String)

/** Mirrors composeApp/src/commonMain/composeResources/files/licenses. */
private val aboutLicences = listOf(
    AboutLicence("application.txt", "VIPTV application — GNU General Public License v2"),
    AboutLicence("onest.txt", "Onest font — SIL Open Font License 1.1"),
    AboutLicence("bricolage-grotesque.txt", "Bricolage Grotesque font — SIL Open Font License 1.1"),
)

@Composable
internal fun TvAboutSettings(local: Boolean, address: String?, profileName: String?, addonCount: Int, enabledCount: Int,
    error: String?, notice: String?, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    openDashboard: (() -> Unit)? = null) {
    var panel by remember { mutableStateOf<TvAboutPanel?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("About", scale)
        TvSettingsRow("Version", VIPTV_VERSION, scale, requester = initial, left = sidebar) { panel = TvAboutPanel.Build }
        TvSettingsRow("Connected to", scale = scale, value = if (local) "Local mode" else address ?: "Not connected", left = sidebar) {
            panel = TvAboutPanel.Connection }
        TvSettingsRow("Send diagnostics", "See exactly what a support report would include", scale = scale, left = sidebar) {
            panel = TvAboutPanel.Diagnostics }
        TvSettingsRow("Privacy", scale = scale, left = sidebar) { panel = TvAboutPanel.Privacy }
        TvSettingsRow("Open-source licences", scale = scale, left = sidebar) { panel = TvAboutPanel.Licences }
        Column(Modifier.padding(top = 16.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            Text("Show and episode data from Simkl.", color = Palette.tertiary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", color = Palette.tertiary,
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
        }
    }
    panel?.let { open ->
        TvAboutPanelContent(open, local, address, profileName, addonCount, enabledCount, error, notice, scale,
            openDashboard) { panel = null }
    }
}

@Composable
private fun TvAboutPanelContent(panel: TvAboutPanel, local: Boolean, address: String?, profileName: String?,
    addonCount: Int, enabledCount: Int, error: String?, notice: String?, scale: Float,
    openDashboard: (() -> Unit)?, dismiss: () -> Unit) {
    when (panel) {
        TvAboutPanel.Build -> AboutDialogFrame("Build", scale, dismiss) {
            AboutParagraph("VIPTV $VIPTV_VERSION", scale)
            AboutParagraph("Experimental native build. There is no update check in this build: updates are installed with a new build.",
                scale, Palette.secondary)
            AboutParagraph("The application is licensed under the GNU General Public License v2. The bundled notices are listed under Open-source licences.",
                scale, Palette.secondary)
        }
        TvAboutPanel.Connection -> AboutDialogFrame("Connection", scale, dismiss) {
            val server = address
            if (local) {
                AboutParagraph("Local mode", scale)
                AboutParagraph("This TV keeps its own profiles, provider connections, My List and progress on this device. There is no server to open.",
                    scale, Palette.secondary)
            } else {
                AboutParagraph(if (server == null) "Not connected to a server" else "Signed in to", scale)
                if (server != null) SelectionContainer {
                    Text(server, color = Palette.text, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp) }
                val open = openDashboard
                if (open != null) {
                    var failed by remember { mutableStateOf(false) }
                    TvAction(Modifier.fillMaxWidth().height(72.dp * scale), scale, activate = {
                        try { open(); failed = false } catch (_: Exception) { failed = true }
                    }) { Text("Open server dashboard", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold) }
                    if (failed) AboutParagraph("Open the address above in a browser on your phone or computer.", scale, Palette.secondary)
                }
            }
        }
        TvAboutPanel.Diagnostics -> AboutDialogFrame("Diagnostics report", scale, dismiss) {
            AboutDetail("App version", VIPTV_VERSION, scale)
            AboutDetail("Connection", if (local) "Local mode" else address ?: "Not connected", scale)
            AboutDetail("Profile", profileName ?: "No profile selected", scale)
            AboutDetail("Add-ons", "$enabledCount of $addonCount enabled", scale)
            error?.let { AboutDetail("Last error", it, scale) }
            notice?.let { AboutDetail("Last message", it, scale) }
            AboutParagraph("Nothing is uploaded from this TV. This build has no diagnostics endpoint, so this list is the whole report.",
                scale, Palette.secondary)
        }
        TvAboutPanel.Privacy -> AboutDialogFrame("Privacy", scale, dismiss) {
            AboutParagraph(if (local) "Profiles, add-on addresses and credentials, My List and viewing progress stay on this TV."
                else "Profiles, My List and viewing progress are stored on the server you signed in to.", scale)
            AboutParagraph("Add-on credentials and signed-in sessions are kept in this device’s secret store. Artwork is downloaded from the metadata sources your add-ons use and cached on this device.",
                scale, Palette.secondary)
            AboutParagraph("This build has no analytics, advertising or crash reporting. Nothing about how you watch leaves the app except the requests your add-ons and signed-in server need.",
                scale, Palette.secondary)
        }
        TvAboutPanel.Licences -> {
            // Read the bundled notices so the list only shows files that ship in
            // this build; a missing resource is reported, never invented.
            var notices by remember { mutableStateOf<List<AboutNotice>?>(null) }
            LaunchedEffect(Unit) {
                notices = aboutLicences.mapNotNull { licence ->
                    try {
                        AboutNotice(licence.title, Res.readBytes("files/licenses/${licence.file}").decodeToString()
                            .lineSequence().first { it.isNotBlank() }.trim())
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
            }
            AboutDialogFrame("Open-source licences", scale, dismiss) {
                val entries = notices
                when {
                    entries == null -> AboutParagraph("Reading the bundled notices…", scale, Palette.secondary)
                    entries.isEmpty() -> AboutParagraph("No licence files are bundled with this build.", scale, Palette.secondary)
                    else -> entries.forEach { notice ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                            AboutParagraph(notice.title, scale)
                            AboutParagraph(notice.firstLine, scale, Palette.secondary)
                        }
                    }
                }
            }
        }
    }
}

/** Same shell as TvLeaveConfirmation / TvSettingsChoiceDialog: dim page, TV type, D-pad Close. */
@Composable
private fun AboutDialogFrame(title: String, scale: Float, dismiss: () -> Unit, width: Dp = 880.dp,
    content: @Composable ColumnScope.() -> Unit) {
    val close = remember { FocusRequester() }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        TvFocusColumn(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Column(Modifier.width(width * scale).clip(RoundedCornerShape(24.dp * scale)).background(Color(0xFF202023))
                    .padding(horizontal = 48.dp * scale, vertical = 44.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    Text(title, fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp, letterSpacing = (-0.88f * scale).sp,
                        fontWeight = FontWeight.Bold)
                    content()
                    TvAction(Modifier.width(220.dp * scale).height(72.dp * scale), scale, requester = close, activate = dismiss) {
                        Text("Close", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { close.requestFocus() }
    }
}

@Composable
private fun AboutParagraph(text: String, scale: Float, color: Color = Palette.body) {
    Text(text, color = color, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp)
}

@Composable
private fun AboutDetail(label: String, value: String, scale: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        Text(label, Modifier.width(190.dp * scale), color = Palette.secondary,
            fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        Text(value, Modifier.weight(1f), color = Palette.text,
            fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
    }
}
