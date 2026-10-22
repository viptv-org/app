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
internal fun TvEntryKeyboard(scale: Float, initialSuffix: Boolean = false, onDelete: (() -> Unit)? = null, onClear: (() -> Unit)? = null, input: (String) -> Unit) {
    var upper by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) { initial.requestFocus() }
    val symbolRows = listOf("1234567890", "!@#$%^&*()", "[]{}<>/\\:;", "'\".,?+=_-~")
    val alphabetRows = listOf("1234567890", "qwertyuiop", "asdfghjkl@", "zxcvbnm.-_")
    val rows = if (symbols) symbolRows else alphabetRows
    Column(verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                row.forEachIndexed { index, character ->
                    val label = if (upper) character.uppercase() else character.toString()
                    TvEntryKey(Modifier.weight(1f), scale, if (!initialSuffix && rowIndex == 1 && index == 0) initial else null,
                        {
                            val current = (if (symbols) symbolRows else alphabetRows)[rowIndex][index]
                            input(if (upper) current.uppercase() else current.toString())
                        }) {
                        Text(label, color = it, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            TvEntryKey(Modifier.width(104.dp * scale), scale, activate = { upper = !upper }) { Icon(Lucide.ArrowUp, "Shift", Modifier.size(28.dp * scale), it) }
            TvEntryKey(Modifier.width(104.dp * scale), scale, activate = { symbols = !symbols }) { Text(if (symbols) "abc" else "#+=", color = it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold) }
            TvEntryKey(Modifier.width(332.dp * scale), scale, activate = { input(" ") }) { Icon(Lucide.Space, "Space", Modifier.size(28.dp * scale), it) }
            // Delete belongs on every entry keyboard; suffix keys stay email-only.
            if (onDelete != null) TvEntryKey(Modifier.width(104.dp * scale), scale, activate = onDelete) { Icon(Lucide.Delete, "Backspace", Modifier.size(28.dp * scale), it) }
            if (onClear != null) TvEntryKey(Modifier.width(160.dp * scale), scale, activate = onClear) { Text("Clear", color = it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold) }
            TvEntryKey(Modifier.width(218.dp * scale), scale, activate = { input(".com") }, suffix = true) { Text(".com", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold) }
            TvEntryKey(Modifier.width(332.dp * scale), scale, requester = if (initialSuffix) initial else null,
                activate = { input("@gmail.com") }, suffix = true) { Text("@gmail.com", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun TvEntryKey(modifier: Modifier, scale: Float, requester: FocusRequester? = null, activate: () -> Unit, suffix: Boolean = false,
    content: @Composable (Color) -> Unit) {
    TvAction(modifier.height(76.dp * scale), scale, requester = requester,
        background = Color.White.copy(alpha = if (suffix) .18f else .09f), cornerRadius = 10.dp * scale, activate = activate) { content(it) }
}
