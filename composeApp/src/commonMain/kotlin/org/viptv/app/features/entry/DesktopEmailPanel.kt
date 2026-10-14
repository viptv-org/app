package org.viptv.app.features.entry

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun DesktopEmailPanel(back: () -> Unit) {
    var code by remember { mutableStateOf("") }
    SignInPanel(440.dp) {
        TextButton(onClick = back, contentPadding = PaddingValues(0.dp)) {
            Text("‹  All sign-in options", color = Palette.text, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Enter the code", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Email sign-in is unavailable. Use a password or continue in your browser.", color = Palette.secondary, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        BasicTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
            decorationBox = { inner ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(6) { index ->
                        Box(Modifier.size(55.dp, 50.dp).border(if (index == code.length) 2.dp else 1.dp,
                            if (index == code.length) Palette.text else Color(0xFF404044), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(code.getOrNull(index)?.toString().orEmpty(), color = Palette.text,
                                fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.size(1.dp)) { inner() }
                }
            })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = back) { Text("Didn’t get it?", color = Palette.secondary, fontSize = 13.sp) }
            Text("Code unavailable", color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp))
        }
    }
}
