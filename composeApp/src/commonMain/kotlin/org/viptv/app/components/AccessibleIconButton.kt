package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

internal val IconButtonTouchTarget = 48.dp

@Composable
internal fun AccessibleIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 16.dp,
    tint: Color = Palette.text,
    wash: Color? = Color.White.copy(alpha = .14f),
    pending: Boolean = false,
) {
    Box(
        modifier.sizeIn(minWidth = IconButtonTouchTarget, minHeight = IconButtonTouchTarget)
            .then(if (wash == null) Modifier else Modifier.clip(CircleShape).background(wash, CircleShape))
            .pointerClickable(enabled = enabled, role = Role.Button, shape = CircleShape, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (pending) MutationSpinner(iconSize, color = tint) else Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}