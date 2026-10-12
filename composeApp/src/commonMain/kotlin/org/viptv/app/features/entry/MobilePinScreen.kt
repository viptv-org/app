package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

private val keyLabels = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("OK" to "", "0" to "", "⌫" to ""),
)

@Composable
internal fun MobilePinScreen(
    profile: Profile?, pin: String, busy: Boolean, error: String?,
    digit: (String) -> Unit, erase: () -> Unit, submit: () -> Unit, back: () -> Unit,
) {
    var forgot by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("×", Modifier.clickable(onClick = back).padding(horizontal = 8.dp), fontSize = 30.sp)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFC24E6B)), contentAlignment = Alignment.Center) {
            Text(profile?.name?.take(1)?.uppercase().orEmpty(), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
        Text("Enter ${profile?.name.orEmpty()}’s PIN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(maxOf(4, pin.length)) { index ->
                Box(Modifier.size(16.dp).clip(CircleShape)
                    .background(if (index < pin.length) Palette.text else Color.Transparent)
                    .then(if (index >= pin.length) Modifier.border(2.dp, Palette.secondary, CircleShape) else Modifier))
            }
        }
        error?.let { Text(it, Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(if (error == null) 120.dp else 32.dp))
        keyLabels.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(25.dp)) {
                row.forEach { (value, letters) ->
                    val enabled = !busy && (value != "OK" || pin.length in 4..8)
                    Box(Modifier.size(78.dp).clip(CircleShape)
                        .background(if (value == "⌫" || value == "OK") Color.Transparent else Color(0xFF222224))
                        .clickable(enabled = enabled) { when (value) { "⌫" -> erase(); "OK" -> submit(); else -> digit(value) } },
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = if (value == "OK") 17.sp else 30.sp,
                                color = if (enabled) Palette.text else Palette.secondary, lineHeight = 32.sp)
                            if (letters.isNotEmpty()) Text(letters, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp, lineHeight = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        TextButton(onClick = { forgot = true }) { Text("Forgot PIN?", fontSize = 15.sp) }
    }
    if (forgot) AlertDialog(onDismissRequest = { forgot = false }, title = { Text("Local profile PIN") },
        text = { Text("This device’s profile PIN cannot be recovered in the app. The device owner can clear app data, which also removes local profiles and settings.") },
        confirmButton = { TextButton(onClick = { forgot = false }) { Text("OK") } })
}
