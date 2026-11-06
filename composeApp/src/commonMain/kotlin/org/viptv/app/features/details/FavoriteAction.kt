package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.viptv.engine.wire.Card
import org.viptv.app.components.MutationFailure
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.saveActionLabel
import org.viptv.app.state.saveTarget

@Composable
internal fun FavoriteAction(card: Card, saved: Boolean, save: (Card) -> Unit) {
    val target = saveTarget(card)
    val mutations = LocalPendingMutation.current
    val pending = mutations.isPending(target)
    Column {
        OutlinedButton(onClick = { save(card) }, shape = RoundedCornerShape(12.dp)) {
            if (pending) MutationSpinner(18.dp) else Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (pending) "Saving…" else saveActionLabel(saved))
        }
        MutationFailure(target, Modifier.padding(top = 6.dp))
    }
}
