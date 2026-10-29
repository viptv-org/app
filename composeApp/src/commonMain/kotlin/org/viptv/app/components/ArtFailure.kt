package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

internal const val ART_ATTEMPTS = 3
private const val ART_RETRY_MILLIS = 400L

// A failed or cancelled image request is not a verdict: the surface keeps its no-art fallback and retries before settling on it.
internal class ArtFailure {
    var failed by mutableStateOf(false)
    var attempts by mutableIntStateOf(0)
}

@Composable
internal fun ArtFailure.retryAfterFailure(): Int {
    LaunchedEffect(failed, attempts) {
        if (!failed || attempts >= ART_ATTEMPTS) return@LaunchedEffect
        delay(ART_RETRY_MILLIS * (attempts + 1))
        attempts++
    }
    return attempts
}
