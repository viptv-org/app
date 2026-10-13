package org.viptv.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
internal actual fun subtitleCacheDirectory(): File = LocalContext.current.applicationContext.cacheDir
