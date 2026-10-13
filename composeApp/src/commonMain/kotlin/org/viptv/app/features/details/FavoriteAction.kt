package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.viptv.engine.wire.Card

@Composable
internal fun FavoriteAction(card: Card, saved: Boolean, save: (Card) -> Unit) {
    OutlinedButton(onClick = { save(card) }, shape = RoundedCornerShape(12.dp)) {
        Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (saved) "Remove from My List" else "Add to My List")
    }
}
