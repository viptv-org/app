package org.viptv.app

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_remote_zip

internal enum class ChromeIcon { Back, Forward, Search, Menu, Cast, Remote }

@Composable
internal fun DesktopChromeIcon(icon: ChromeIcon, color: Color, modifier: Modifier) {
    if (icon == ChromeIcon.Remote) {
        Icon(painterResource(Res.drawable.ic_remote_zip), null, modifier, tint = color)
        return
    }
    val vector = when (icon) {
        ChromeIcon.Back -> Lucide.ChevronLeft
        ChromeIcon.Forward -> Lucide.ChevronRight
        ChromeIcon.Search -> Lucide.Search
        ChromeIcon.Menu -> Lucide.List
        ChromeIcon.Cast -> Lucide.Cast
        ChromeIcon.Remote -> error("Remote uses the ZIP asset")
    }
    Icon(vector, null, modifier, tint = color)
}
