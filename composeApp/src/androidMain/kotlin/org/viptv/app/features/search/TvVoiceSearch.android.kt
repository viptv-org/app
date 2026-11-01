package org.viptv.app.features.search

import android.content.Intent
import android.content.res.Configuration
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import org.viptv.app.components.TvAction

private fun voiceSearchIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your search")
}

private fun voiceSearchAvailable(context: PlatformContext): Boolean {
    val television = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val recognizerAvailable = runCatching { context.packageManager.resolveActivity(voiceSearchIntent(), 0) != null }.getOrDefault(false)
    return tvVoiceInputAvailable(television, recognizerAvailable)
}

@Composable
internal actual fun TvVoiceSearchAction(scale: Float, onRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val available = remember(context) { voiceSearchAvailable(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val candidates = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        tvVoiceRecognitionText(result.resultCode == android.app.Activity.RESULT_OK, candidates)?.let(onRecognized)
    }
    if (available) {
        TvAction(Modifier.size(72.dp * scale), scale, round = true, ring = false,
            activate = { runCatching { launcher.launch(voiceSearchIntent()) } }) {
            Icon(Lucide.Mic, "Search by voice", Modifier.size(30.dp * scale), it)
        }
    }
}
