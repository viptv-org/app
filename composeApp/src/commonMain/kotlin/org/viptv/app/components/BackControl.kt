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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide

internal const val PageBackTag = "page-back"
internal const val PageBackGlyphTag = "page-back-glyph"

@Composable
fun BackControl(onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null,
    desktop: Boolean = false, glass: Boolean = false) {
    val iconSize = if (desktop) 20.dp else 24.dp
    val shape = if (glass) CircleShape else RoundedCornerShape(8.dp)
    if (label == null) {
        // The 44 hit target straddles the glyph, so the glyph's left edge sits on the slot's leading edge.
        Box(modifier.offset(x = (iconSize - 44.dp) / 2).size(44.dp)
            .then(if (glass) Modifier.background(Color.White.copy(alpha = .12f), CircleShape) else Modifier)) {
            Box(Modifier.matchParentSize()
                .pointerClickable(onClick = onClick, role = Role.Button, shape = shape)
                .semantics { contentDescription = "Back"; testTag = PageBackTag })
            Icon(Lucide.ChevronLeft, null, Modifier.size(iconSize).align(Alignment.Center).testTag(PageBackGlyphTag))
        }
    } else {
        Box(modifier.height(44.dp)) {
            Box(Modifier.matchParentSize()
                .pointerClickable(onClick = onClick, role = Role.Button, shape = shape)
                .semantics { contentDescription = label; testTag = PageBackTag })
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.ChevronLeft, null, Modifier.size(iconSize).testTag(PageBackGlyphTag))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = if (desktop) 14.sp else 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}