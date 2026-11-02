package org.viptv.app.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit

internal class DomTextSpec(
    val value: String,
    val onValueChange: (String) -> Unit,
    val label: String,
    val placeholder: String?,
    val enabled: Boolean,
    val secret: Boolean,
    val keyboardType: KeyboardType,
    val onEnter: (() -> Unit)?,
    val textColor: Color,
    val fontSize: TextUnit,
    val interactionSource: MutableInteractionSource?,
)

internal interface DomTextHost {
    @Composable
    fun Field(spec: DomTextSpec, modifier: Modifier, decoration: @Composable (input: @Composable (Modifier) -> Unit) -> Unit)
}

internal val LocalDomTextHost = staticCompositionLocalOf<DomTextHost?> { null }
