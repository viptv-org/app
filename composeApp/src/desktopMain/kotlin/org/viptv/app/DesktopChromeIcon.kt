package org.viptv.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class ChromeIcon { Back, Forward, Search, Menu, Cast, Remote }

@Composable
internal fun DesktopChromeIcon(icon: ChromeIcon, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val stroke = 1.9.dp.toPx()
        val center = Offset(width / 2, height / 2)
        when (icon) {
            ChromeIcon.Back, ChromeIcon.Forward -> {
                val direction = if (icon == ChromeIcon.Back) -1 else 1
                val tip = center.x + direction * width * .18f
                val base = center.x - direction * width * .14f
                drawLine(color, Offset(base, height * .22f), Offset(tip, height * .5f), stroke)
                drawLine(color, Offset(tip, height * .5f), Offset(base, height * .78f), stroke)
            }
            ChromeIcon.Search -> {
                drawCircle(color, width * .27f, Offset(width * .42f, height * .42f), style = Stroke(stroke))
                drawLine(color, Offset(width * .63f, height * .63f), Offset(width * .88f, height * .88f), stroke)
            }
            ChromeIcon.Menu -> for (fraction in listOf(.27f, .5f, .73f))
                drawLine(color, Offset(width * .18f, height * fraction), Offset(width * .82f, height * fraction), stroke)
            ChromeIcon.Cast -> {
                drawRoundRect(color, Offset(width * .12f, height * .16f), Size(width * .76f, height * .68f),
                    CornerRadius(width * .1f), style = Stroke(stroke))
                drawArc(color, 270f, 90f, false, Offset(width * .06f, height * .49f),
                    Size(width * .48f, height * .48f), style = Stroke(stroke))
                drawCircle(color, width * .055f, Offset(width * .16f, height * .84f))
            }
            ChromeIcon.Remote -> {
                drawRoundRect(color, Offset(width * .3f, height * .08f), Size(width * .4f, height * .84f),
                    CornerRadius(width * .12f), style = Stroke(stroke))
                drawCircle(color, width * .08f, Offset(width * .5f, height * .38f), style = Stroke(stroke))
                drawCircle(color, width * .03f, Offset(width * .5f, height * .7f))
            }
        }
    }
}
