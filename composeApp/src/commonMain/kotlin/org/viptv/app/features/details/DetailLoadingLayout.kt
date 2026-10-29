package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun DetailLoadingLayout(television: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().semantics { contentDescription = "Loading title details" }) {
        val scale = if (television) maxWidth / 1920.dp else 1f
        val phone = !television && maxWidth < 640.dp
        val gutter = if (television) 96.dp * scale else if (phone) 20.dp else 40.dp
        Column(Modifier.fillMaxSize().padding(horizontal = gutter)
            .padding(top = if (television) 172.dp * scale else if (phone) 220.dp else 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            DetailPlaceholder(Modifier.width(if (television) 560.dp * scale else 220.dp).height(66.dp * scale))
            DetailPlaceholder(Modifier.width(if (television) 440.dp * scale else 200.dp).height(22.dp * scale))
            repeat(3) { DetailPlaceholder(Modifier.fillMaxWidth(if (television) .44f else .85f).height(20.dp * scale)) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                repeat(2) { DetailPlaceholder(Modifier.width(if (television) 190.dp * scale else 120.dp).height(64.dp * scale)) }
            }
            Spacer(Modifier.height(if (television) 112.dp * scale else 28.dp))
            DetailPlaceholder(Modifier.width(240.dp * scale).height(42.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                repeat(if (phone) 2 else 4) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        DetailPlaceholder(Modifier.fillMaxWidth().aspectRatio(16f / 9))
                        DetailPlaceholder(Modifier.fillMaxWidth(.8f).height(20.dp * scale))
                        DetailPlaceholder(Modifier.fillMaxWidth(.5f).height(16.dp * scale))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPlaceholder(modifier: Modifier) {
    Box(modifier.background(Palette.raised, RoundedCornerShape(8.dp)))
}
