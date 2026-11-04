package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

internal const val LIST_END_COPY = "You’ve reached the end."
internal const val LIST_LOAD_MORE_COPY = "Couldn’t load more"
internal const val LIST_RETRY_COPY = "Retry"
internal const val LIST_TITLES = "titles"
internal const val LIST_CHANNELS = "channels"

private fun loadingCopy(noun: String) = "Loading more $noun…"

@Composable
internal fun ListEndFooter(loading: Boolean = false, ended: Boolean = false, failed: Boolean = false,
    retry: (() -> Unit)? = null, noun: String = LIST_TITLES, modifier: Modifier = Modifier,
    television: Boolean = false, scale: Float = 1f) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            failed -> InlineRetryTile(retry, Modifier.fillMaxWidth(), television, scale)
            loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale),
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size((if (television) 28 else 14).dp * scale),
                    color = Palette.secondary, strokeWidth = (if (television) 3 else 2).dp * scale)
                Text(loadingCopy(noun), color = Palette.secondary,
                    fontSize = (if (television) 20f * scale else 13f).sp,
                    lineHeight = (if (television) 26f * scale else 17f).sp)
            }
            ended -> Text(LIST_END_COPY, color = Palette.tertiary,
                fontSize = (if (television) 20f * scale else 13f).sp,
                lineHeight = (if (television) 26f * scale else 17f).sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun InlineRetryTile(retry: (() -> Unit)?, modifier: Modifier = Modifier, television: Boolean = false, scale: Float = 1f) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(LIST_LOAD_MORE_COPY, fontSize = (if (television) 22f * scale else 12f).sp,
            lineHeight = (if (television) 28f * scale else 16f).sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height((if (television) 20 else 12).dp * scale))
        if (television) {
            TvAction(Modifier.size(48.dp * scale), scale, round = true, activate = retry ?: {}) { tint ->
                Icon(Lucide.RotateCw, LIST_RETRY_COPY, Modifier.size(20.dp * scale), tint = tint)
            }
        } else {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Palette.raised)
                .pointerClickable(onClickLabel = LIST_RETRY_COPY, role = Role.Button, shape = CircleShape, onClick = retry ?: {}),
                contentAlignment = Alignment.Center) {
                Icon(Lucide.RotateCw, LIST_RETRY_COPY, Modifier.size(20.dp), tint = Palette.text)
            }
        }
        Spacer(Modifier.height((if (television) 10 else 6).dp * scale))
        Text(LIST_RETRY_COPY, color = Palette.secondary, fontSize = (if (television) 18f * scale else 12f).sp,
            fontWeight = FontWeight.Medium)
    }
}
