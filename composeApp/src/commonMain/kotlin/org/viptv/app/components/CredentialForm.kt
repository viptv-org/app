package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun CredentialForm(
    fields: List<CredentialField>,
    submitLabel: String,
    valid: Boolean,
    busy: Boolean,
    error: String?,
    onChange: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    storePasswordOnSuccess: Boolean = false,
)