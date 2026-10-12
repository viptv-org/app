package org.viptv.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Experiment", state = rememberWindowState(width = 1440.dp, height = 900.dp)) {
        val base = when {
            System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            System.getProperty("os.name").startsWith("Mac") -> System.getProperty("user.home") + "/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: System.getProperty("user.home") + "/.local/share"
        }
        App(localDatabase = "$base/viptv-experiment/local.sqlite")
    }
}
