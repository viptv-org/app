package org.viptv.app.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

// A scrim is dismissed by the labelled control on the sheet, so a screen reader never meets the blank surface behind it.
internal fun Modifier.dismissScrim(onClick: () -> Unit): Modifier =
    clearAndSetSemantics {}.pointerClickable(onClick = onClick)

internal fun Modifier.dismissScrim(interactionSource: MutableInteractionSource, onClick: () -> Unit): Modifier =
    clearAndSetSemantics {}.pointerClickable(interactionSource = interactionSource, indication = null, onClick = onClick)