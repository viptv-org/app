package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import org.viptv.app.state.MutationTarget
import org.viptv.app.state.PendingMutations
import org.viptv.app.theme.Palette

/** The pending state of every mutating control, read by the element it acts on. */
internal class PendingMutationUi(val pending: PendingMutations = PendingMutations(), val retry: (MutationTarget) -> Unit = {}) {
    fun isPending(target: MutationTarget) = pending.isPending(target)
    fun failure(target: MutationTarget) = pending.failure(target)
}

internal val LocalPendingMutation = compositionLocalOf { PendingMutationUi() }

internal fun saveActionLabel(saved: Boolean) = if (saved) "Remove from My List" else "Add to My List"

// The name belongs to the control: the pending state swaps its icon for an unnamed spinner.
internal fun Modifier.saveActionName(saved: Boolean) = semantics { contentDescription = saveActionLabel(saved) }

@Composable
internal fun MutationSpinner(size: Dp, color: Color = Palette.text, strokeWidth: Dp = 2.dp, scale: Float = 1f) {
    CircularProgressIndicator(Modifier.size(size * scale), color = color, strokeWidth = strokeWidth * scale)
}

/** The inline error and Retry a failed write leaves beside the control it acted on. */
@Composable
private fun InlineMutationError(message: String, retry: () -> Unit, modifier: Modifier = Modifier, scale: Float = 1f) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        Icon(Lucide.CircleAlert, null, Modifier.size(16.dp * scale), tint = Palette.danger)
        Text(message, Modifier.weight(1f, fill = false), color = Palette.danger, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp)
        TextButton(onClick = retry, contentPadding = PaddingValues(horizontal = 8.dp * scale)) {
            Text("Retry", color = Palette.text, fontSize = (13 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The failure line a surface draws beside the control it acted on, and nothing while its write is well. */
@Composable
internal fun MutationFailure(target: MutationTarget, modifier: Modifier = Modifier, scale: Float = 1f) {
    val mutations = LocalPendingMutation.current
    mutations.failure(target)?.let { message ->
        InlineMutationError(message, retry = { mutations.retry(target) }, modifier = modifier, scale = scale)
    }
}
