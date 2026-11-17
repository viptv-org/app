package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SkipForward
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.theme.Palette

@Composable
internal fun PlayerSkipPrompt(skip: TvSkipRange, insets: WindowInsets, bottom: Dp, activate: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
        Button(activate, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = bottom).height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
            contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(Lucide.SkipForward, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(skip.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun PlayerSkippedNotice(skip: TvSkipRange, insets: WindowInsets, bottom: Dp, undo: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = bottom).height(44.dp)
            .clip(RoundedCornerShape(12.dp)).background(Palette.raised).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(skip.skippedLabel, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            TextButton(undo, Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(undoSkipLabel, color = Palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
