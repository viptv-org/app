package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import org.viptv.app.features.entry.AvatarPickerLayout
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
    Column(Modifier.fillMaxWidth()) {
        TvSettingsHeading("Profile", scale)
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow("Name", scale = scale, value = values.profile.name, requester = first, left = sidebar) {
            if (!busy) dialogs.name = true
        }
        Spacer(Modifier.height(8.dp * scale))
        TvSettingsRow("Avatar", scale = scale, value = "Change", left = sidebar, leading = Lucide.Pencil) {
            if (!busy) dialogs.avatar = true
        }
        if (!values.local) {
            Spacer(Modifier.height(8.dp * scale))
            TvSettingsRow("Kids profile", "Limits titles, search and Live for this profile.", scale, checked = values.kids,
                left = sidebar) { if (!busy) dialogs.toggleKids(values, actions) }
            Spacer(Modifier.height(8.dp * scale))
            TvSettingsRow("Maturity limit", "Titles above this rating are hidden for this profile", scale,
                value = maturityLimitValue(values), left = sidebar) { if (!busy) dialogs.maturity = true }
            if (values.kids) {
                Spacer(Modifier.height(8.dp * scale))
                TvSettingsRow("Approved titles", "Only these titles show in the kids library", scale,
                    value = values.approvedTitles?.toString() ?: "—", left = sidebar) { actions.approvedTitles() }
            }
        }
        Spacer(Modifier.height(8.dp * scale))
        if (values.local) {
            TvSettingsRow("Profile lock", "Ask for a PIN before this profile opens", scale, checked = values.profile.locked,
                left = sidebar) { if (!busy) dialogs.lock = true }
        } else {
            TvSettingsRow("Profile lock", "Ask for a PIN before this profile opens", scale, left = sidebar) {
                if (!busy) openParental()
            }
        }
        Spacer(Modifier.height(8.dp * scale))
        TvSettingsRow("Language", "Default audio and subtitles for this profile", scale,
            value = languageSummary(values.audioLanguage, values.subtitleLanguage), left = sidebar) { actions.language() }
        error?.let { Text(it, Modifier.padding(top = 16.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
        if (busy) Text("Saving…", Modifier.padding(top = 16.dp * scale), color = Palette.secondary, fontSize = (22 * scale).sp)
        if (values.canDelete) {
            Spacer(Modifier.height(40.dp * scale))
            TvSettingsRow("Delete profile", scale = scale, rowHeight = 80, labelColor = Palette.danger, left = sidebar) {
                if (!busy) dialogs.delete = true
            }
        }
    }
    ProfileDialogsContent(dialogs, values, busy, error, actions, AvatarPickerLayout.Television)
}