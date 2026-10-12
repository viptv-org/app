package org.viptv.app.features.entry

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

internal enum class SignInMethod { Browser, Email, Password, Device, Iptv, Local }

@Composable
internal fun SignInMethodIcon(method: SignInMethod, modifier: Modifier) {
    val icon = when (method) {
        SignInMethod.Browser -> Lucide.ExternalLink
        SignInMethod.Email -> Lucide.Mail
        SignInMethod.Password -> Lucide.KeyRound
        SignInMethod.Device -> Lucide.ScanQrCode
        SignInMethod.Iptv -> Lucide.Server
        SignInMethod.Local -> Lucide.UserRound
    }
    Icon(icon, null, modifier, tint = Palette.text)
}
