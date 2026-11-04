package org.viptv.app.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val PENDING_PLAYBACK_LABEL = "Starting playback"

val LocalPendingAction = compositionLocalOf<String?> { null }

@Composable
internal fun Card.isPlayPending(): Boolean = LocalPendingAction.current == cardLookupKey(this)

@Composable
internal fun SourceCandidate.isPlayPending(): Boolean = LocalPendingAction.current == id

@Composable
internal fun PendingActionSpinner(modifier: Modifier = Modifier, size: Dp = 16.dp,
    strokeWidth: Dp = 2.dp, color: Color = Color.White, label: String = PENDING_PLAYBACK_LABEL) {
    CircularProgressIndicator(modifier.size(size).clearAndSetSemantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, color = color, strokeWidth = strokeWidth)
}