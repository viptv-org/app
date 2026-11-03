package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide

internal const val PageBackTag = "page-back"

@Composable
fun BackControl(onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null,
    desktop: Boolean = false, glass: Boolean = false) {
    val iconSize = if (desktop) 20.dp else 24.dp
    val shape = if (glass) CircleShape else RoundedCornerShape(8.dp)
    if (label == null) {
        Box(modifier.size(44.dp)
            .then(if (glass) Modifier.background(Color.White.copy(alpha = .12f), CircleShape) else Modifier)
            .pointerClickable(onClick = onClick, role = Role.Button, shape = shape)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Back"
                testTag = PageBackTag
                onClick { onClick(); true }
            }, contentAlignment = Alignment.Center) {
            Icon(Lucide.ChevronLeft, null, Modifier.size(iconSize))
        }
    } else {
        Row(modifier.height(44.dp)
            .pointerClickable(onClick = onClick, role = Role.Button, shape = shape)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                testTag = PageBackTag
                onClick { onClick(); true }
            }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ChevronLeft, null, Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = if (desktop) 14.sp else 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}