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
import com.composables.icons.lucide.RotateCcw
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
private fun InlineMutationError(message: String, retry: (() -> Unit)?, modifier: Modifier = Modifier, scale: Float = 1f) {
    val icon: @Composable () -> Unit = { Icon(Lucide.CircleAlert, null, Modifier.size(16.dp * scale), tint = Palette.danger) }
    val line: @Composable (Modifier) -> Unit = { text ->
        Text(message, text, color = Palette.danger, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp)
    }
    val again: @Composable () -> Unit = {
        if (retry != null) TextButton(onClick = retry, contentPadding = PaddingValues(horizontal = 8.dp * scale)) {
            Text("Retry", color = Palette.text, fontSize = (13 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    }
    // Narrower than a menu row the message and Retry stop sharing a line, so the Retry drops under the message.
    BoxWithConstraints(modifier) {
        if (maxWidth < 220.dp * scale) Column(verticalArrangement = Arrangement.spacedBy(2.dp * scale)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                icon(); line(Modifier.weight(1f))
            }
            again()
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            icon(); line(Modifier.weight(1f, fill = false)); again()
        }
    }
}

/**
 * The failure line a surface draws beside the control it acted on, and nothing while its write is well.
 * A TV card leaves [retry] to its title menu: a button nested in a grid cell never takes the D-pad.
 */
@Composable
internal fun MutationFailure(target: MutationTarget, modifier: Modifier = Modifier, scale: Float = 1f, retry: Boolean = true) {
    val mutations = LocalPendingMutation.current
    mutations.failure(target)?.let { message ->
        InlineMutationError(message, retry = if (retry) ({ mutations.retry(target) }) else null, modifier = modifier, scale = scale)
    }
}

/** A failed TV save turns its control into Retry, so the remote can run the same write again. */
@Composable
internal fun TvRetryAction(target: MutationTarget, scale: Float, modifier: Modifier = Modifier, ring: Boolean = false) {
    val mutations = LocalPendingMutation.current
    TvAction(modifier, scale, ring = ring, activate = { mutations.retry(target) },
        contentPadding = PaddingValues(horizontal = 24.dp * scale), arrangement = Arrangement.Start) { tint ->
        Icon(Lucide.RotateCcw, null, Modifier.size(28.dp * scale), tint)
        Spacer(Modifier.width(10.dp * scale))
        Text("Retry", color = tint, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
    }
}
