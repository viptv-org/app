package org.viptv.app.features.entry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Brand
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.StartupProgress
import org.viptv.app.theme.Palette

@Composable
internal fun StartupScreen(changeConnection: (() -> Unit)? = null) {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opacity.animateTo(1f, tween(600, easing = CubicBezierEasing(0f, 0f, .58f, 1f))) }
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Starting VIPTV" }) {
        val phone = maxWidth < 640.dp
        val tablet = maxWidth < 1024.dp
        val icon = if (phone) 44.dp else if (tablet) 60.dp else 72.dp
        Column(Modifier.align(Alignment.Center).padding(24.dp).graphicsLayer { alpha = opacity.value },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Brand(icon, (icon.value * 1.18f).sp, icon * 0.26f, (-0.04f * icon.value).sp, (icon.value * 1.18f).sp)
            Spacer(Modifier.height(if (phone) 32.dp else 44.dp))
            StartupProgress(if (phone) .5f else if (tablet) .7f else .85f)
            Spacer(Modifier.height(if (phone) 22.dp else 28.dp))
            Text("Getting VIPTV ready…", color = Palette.secondary, fontSize = if (phone) 15.sp else if (tablet) 17.sp else 18.sp)
            if (changeConnection != null) {
                Spacer(Modifier.height(if (phone) 30.dp else 38.dp))
                OutlinedButton(onClick = changeConnection) { Text("Change connection", fontSize = if (phone) 15.sp else 16.sp) }
            }
        }
    }
}