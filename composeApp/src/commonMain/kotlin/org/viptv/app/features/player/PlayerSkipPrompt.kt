package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SkipForward
import org.viptv.app.components.Button
import org.viptv.app.theme.Palette

@Composable
internal fun PlayerSkipPrompt(skip: TvSkipRange, insets: WindowInsets, activate: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
        Button(activate, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 104.dp).height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
            contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(Lucide.SkipForward, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(skip.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
