package org.viptv.app.features.details

import androidx.compose.runtime.Composable

/** Covers the first browser key before a pointer/Tab moves DOM focus into Compose. */
@Composable
internal expect fun InitialBrowserEscape(dismiss: () -> Unit)
