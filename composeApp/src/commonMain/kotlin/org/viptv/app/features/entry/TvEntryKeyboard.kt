package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction

@Composable
internal fun TvEntryKeyboard(scale: Float, input: (String) -> Unit) {
    var upper by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) { initial.requestFocus() }
    val rows = if (symbols) listOf("1234567890", "!@#$%^&*()", "[]{}<>/\\:;", "'\".,?+=_-~")
        else listOf("1234567890", "qwertyuiop", "asdfghjkl@", "zxcvbnm.-_")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                row.forEachIndexed { index, character ->
                    val label = if (upper) character.uppercase() else character.toString()
                    TvEntryKey(Modifier.weight(1f), scale, if (rowIndex == 1 && index == 0) initial else null, { input(label) }) {
                        Text(label, color = it, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            TvEntryKey(Modifier.weight(1f), scale, activate = { upper = !upper }) { Icon(Lucide.ArrowUp, "Shift", Modifier.size(28.dp * scale), it) }
            TvEntryKey(Modifier.weight(1f), scale, activate = { symbols = !symbols }) { Text(if (symbols) "abc" else "#+=", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold) }
            TvEntryKey(Modifier.weight(3f), scale, activate = { input(" ") }) { Icon(Lucide.Space, "Space", Modifier.size(28.dp * scale), it) }
            TvEntryKey(Modifier.weight(2f), scale, activate = { input(".com") }) { Text(".com", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold) }
            TvEntryKey(Modifier.weight(3f), scale, activate = { input("@gmail.com") }) { Text("@gmail.com", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TvEntryKey(modifier: Modifier, scale: Float, requester: FocusRequester? = null, activate: () -> Unit,
    content: @Composable (Color) -> Unit) {
    TvAction(modifier.height(76.dp * scale), scale, requester = requester, background = Color(0xFF222224), activate = activate) { content(it) }
}
