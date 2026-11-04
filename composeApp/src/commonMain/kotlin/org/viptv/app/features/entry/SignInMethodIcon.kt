package org.viptv.app.features.entry

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

internal enum class SignInMethod { Browser, Password, Device, Local, Account }

@Composable
internal fun SignInMethodIcon(method: SignInMethod, modifier: Modifier) {
    val icon = when (method) {
        SignInMethod.Browser -> Lucide.ExternalLink
        SignInMethod.Password -> Lucide.KeyRound
        SignInMethod.Device -> Lucide.ScanQrCode
        SignInMethod.Local -> Lucide.UserRound
        SignInMethod.Account -> Lucide.UserPlus
    }
    Icon(icon, null, modifier, tint = Palette.text)
}
