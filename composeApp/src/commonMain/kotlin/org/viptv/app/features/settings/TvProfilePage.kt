package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import org.viptv.app.theme.Palette

@Composable
internal fun TvProfileSettingsPage(values: ProfilePageValues?, busy: Boolean, error: String?, saved: Long,
    actions: ProfilePageActions, scale: Float, first: FocusRequester, sidebar: FocusRequester,
    openParental: () -> Unit) {
    val dialogs = rememberProfileDialogs(values?.profile?.id.orEmpty(), saved)
    if (values == null) {
        TvSettingsHeading("Profile", scale)
        Text("Choose a profile in Switch profile to edit it.", Modifier.padding(top = 24.dp * scale), color = Palette.secondary,
            fontSize = (22 * scale).sp)
        return
    }
    val avatarRow = remember(values.profile.id) { FocusRequester() }
    val kidsRow = remember(values.profile.id) { FocusRequester() }
    val maturityRow = remember(values.profile.id) { FocusRequester() }
    val lockRow = remember(values.profile.id) { FocusRequester() }
    val languageRow = remember(values.profile.id) { FocusRequester() }
    val deleteRow = remember(values.profile.id) { FocusRequester() }
    var restore by remember(values.profile.id) { mutableStateOf<FocusRequester?>(null) }
    fun openDialog(target: FocusRequester, dialog: () -> Unit) { restore = target; dialog() }
    val anyDialogOpen = dialogs.name || dialogs.avatar || dialogs.lock || dialogs.maturity || dialogs.delete ||
        dialogs.householdPinTitle != null
    LaunchedEffect(anyDialogOpen) {
        if (anyDialogOpen) return@LaunchedEffect
        withFrameNanos { }
        restore?.let { target -> runCatching { target.requestFocus() }; restore = null }
    }
    Column(Modifier.fillMaxWidth()) {
        TvSettingsHeading("Profile", scale)
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow("Name", scale = scale, value = values.profile.name, requester = first, left = sidebar) {
            if (!busy) openDialog(first) { dialogs.name = true }
        }
        Spacer(Modifier.height(8.dp * scale))
        TvSettingsRow("Avatar", scale = scale, value = "Change", left = sidebar, leading = Lucide.Pencil, requester = avatarRow) {
            if (!busy) openDialog(avatarRow) { dialogs.avatar = true }
        }
        if (!values.local) {
            Spacer(Modifier.height(8.dp * scale))
            TvSettingsRow("Kids profile",
                if (values.limitsKnown) "Limits titles, search and Live for this profile." else limitsUnknownNote(values),
                scale, checked = values.kids, left = sidebar, requester = kidsRow) {
                if (values.limitsKnown) openDialog(kidsRow) { dialogs.toggleKids(values, actions) } else openParental()
            }
            Spacer(Modifier.height(8.dp * scale))
            TvSettingsRow("Maturity limit",
                if (values.limitsKnown) "Titles above this rating are hidden for this profile" else limitsUnknownNote(values),
                scale, value = maturityLimitValue(values), left = sidebar, requester = maturityRow) {
                if (values.limitsKnown) openDialog(maturityRow) { dialogs.maturity = true } else openParental()
            }
            if (values.kids) {
                Spacer(Modifier.height(8.dp * scale))
                TvSettingsRow("Approved titles",
                    if (values.limitsKnown) "Only these titles show in the kids library" else limitsUnknownNote(values),
                    scale, value = approvedTitlesValue(values), left = sidebar) {
                    if (values.limitsKnown) actions.approvedTitles() else openParental()
                }
            }
        }
        Spacer(Modifier.height(8.dp * scale))
        if (values.local) {
            TvSettingsRow("Profile lock", "Ask for a PIN before this profile opens", scale, checked = values.profile.locked,
                left = sidebar, requester = lockRow) { if (!busy) openDialog(lockRow) { dialogs.lock = true } }
        } else {
            TvSettingsRow("Profile lock", "Ask for a PIN before this profile opens", scale, left = sidebar, requester = lockRow) {
                if (!busy) openDialog(lockRow) { openParental() }
            }
        }
        Spacer(Modifier.height(8.dp * scale))
        TvSettingsRow("Language", "Default audio and subtitles for this profile", scale,
            value = languageSummary(values.audioLanguage, values.subtitleLanguage), left = sidebar, requester = languageRow) { actions.language() }
        error?.let { Text(it, Modifier.padding(top = 16.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
        if (busy) Text("Saving…", Modifier.padding(top = 16.dp * scale), color = Palette.secondary, fontSize = (22 * scale).sp)
        if (values.canDelete) {
            Spacer(Modifier.height(40.dp * scale))
            TvSettingsRow("Delete profile", scale = scale, rowHeight = 80, labelColor = Palette.danger, left = sidebar,
                requester = deleteRow) { if (!busy) openDialog(deleteRow) { dialogs.delete = true } }
        }
    }
    TvProfileDialogsContent(dialogs, values, busy, error, actions, scale)
}
