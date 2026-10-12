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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

private const val SIGN_IN_URL = "https://viptv.syek.tech"

@Composable
internal fun DesktopBrowserPanel(back: () -> Unit) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    SignInPanel(440.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(32.dp), color = Palette.secondary, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text("Finish signing in in your browser", color = Palette.text,
                fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Browser sign-in is not connected yet. You can open viptv.syek.tech, then choose another sign-in method here.",
                color = Palette.secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { uri.openUri(SIGN_IN_URL) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303033), contentColor = Palette.text)) {
                    Text("Open browser again", fontSize = 13.sp)
                }
                Button(onClick = { clipboard.setText(AnnotatedString(SIGN_IN_URL)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303033), contentColor = Palette.text)) {
                    Text("Copy link", fontSize = 13.sp)
                }
            }
            Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                Text("‹  All sign-in options", Modifier.clickable(onClick = back),
                    color = Palette.text, fontSize = 13.sp)
            }
        }
    }
}

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
        Text("Email-code delivery is not connected yet. This entry state previews the supplied design.", color = Palette.secondary, fontSize = 14.sp)
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
