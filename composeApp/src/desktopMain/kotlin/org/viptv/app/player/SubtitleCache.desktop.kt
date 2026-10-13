package org.viptv.app.player

import androidx.compose.runtime.Composable
import java.io.File

@Composable
internal actual fun subtitleCacheDirectory(): File = File(System.getProperty("java.io.tmpdir"))
