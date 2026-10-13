package org.viptv.app.features.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun AddonUrlField(state: TextFieldState, enabled: Boolean, modifier: Modifier, dismiss: (() -> Unit)?)
