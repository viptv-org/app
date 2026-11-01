package org.viptv.app.features.search

import androidx.compose.runtime.Composable

internal fun tvVoiceInputAvailable(television: Boolean, recognizerAvailable: Boolean): Boolean =
    television && recognizerAvailable

internal fun tvVoiceRecognitionText(resultOk: Boolean, candidates: List<String>?): String? =
    candidates?.firstOrNull()?.takeIf { resultOk && it.isNotBlank() }

@Composable
internal expect fun TvVoiceSearchAction(scale: Float, onRecognized: (String) -> Unit)
