package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun DesktopDevicePanel(back: () -> Unit) {
    SignInPanel(480.dp) {
        TextButton(onClick = back, contentPadding = PaddingValues(0.dp)) {
            Text("‹  All sign-in options", color = Palette.text, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Use another device", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("On a phone or TV that’s signed in to VIPTV, open Settings › Sign in a device and scan this code — or go to viptv.syek.tech/device and type it.",
            color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(135.dp).background(Palette.text, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("▦\nCode pending", color = Palette.background, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Code", color = Palette.secondary, fontSize = 12.sp)
                Text("— — — —", color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Device approval is not connected yet", color = Palette.secondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Device approval will be available when the connection flow is added.", color = Palette.secondary, fontSize = 13.sp)
    }
}

@Composable
internal fun DesktopIptvPanel(back: () -> Unit) {
    var tab by remember { mutableStateOf("Xtream Codes") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }
    SignInPanel(480.dp) {
        TextButton(onClick = back, contentPadding = PaddingValues(0.dp)) {
            Text("‹  All sign-in options", color = Palette.text, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Sign in with your IPTV provider", color = Palette.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF303033), RoundedCornerShape(18.dp)).padding(4.dp)) {
            for (option in listOf("Xtream Codes", "M3U playlist")) {
                Box(Modifier.weight(1f).fillMaxHeight().background(if (tab == option) Palette.background else Color.Transparent,
                    RoundedCornerShape(15.dp)).clickable { tab = option }, contentAlignment = Alignment.Center) {
                    Text(option, color = if (tab == option) Palette.text else Palette.secondary,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (tab == "Xtream Codes") {
            IptvField("Server address", server, "http://provider.example:8080") { server = it }
            IptvField("Username", username, "Username") { username = it }
            Text("Password", color = Palette.text, fontSize = 12.sp)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Password", color = Palette.secondary) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
        } else {
            IptvField("M3U playlist link", playlist, "https://provider.example/playlist.m3u") { playlist = it }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Text("Connect", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Provider connection is not available yet. Your details have not been saved.",
            color = Palette.secondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun IptvField(label: String, value: String, hint: String, change: (String) -> Unit) {
    Text(label, color = Palette.text, fontSize = 12.sp)
    Spacer(Modifier.height(5.dp))
    OutlinedTextField(value, change, Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = Palette.secondary) }, singleLine = true)
    Spacer(Modifier.height(10.dp))
}
