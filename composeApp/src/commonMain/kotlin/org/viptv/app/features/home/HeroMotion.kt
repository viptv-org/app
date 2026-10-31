package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.conflate
import org.viptv.app.components.Motion
import org.viptv.app.components.motionSpec
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card

@Composable
internal fun HeroCopyFade(card: Card, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, content: @Composable (Card) -> Unit) {
    val shown = remember { mutableStateOf(card) }
    val outgoing = remember { mutableStateOf<Card?>(null) }
    val fade = remember { Animatable(1f) }
    val arrive by rememberUpdatedState(motionSpec<Float>(tween(Motion.arrive, easing = Motion.standard)))
    val latest by rememberUpdatedState(card)
    val density = LocalDensity.current
    var floor by remember { mutableStateOf(0.dp) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.conflate().collect { next ->
            if (cardLookupKey(next) == cardLookupKey(shown.value)) { shown.value = next; return@collect }
            if (fade.value >= 1f) { outgoing.value = shown.value; fade.snapTo(0f) }
            shown.value = next
            fade.animateTo(1f, arrive)
            outgoing.value = null
        }
    }
    Box(modifier.heightIn(min = floor).onGloballyPositioned {
        floor = maxOf(floor, with(density) { it.size.height.toDp() })
    }, contentAlignment = alignment) {
        outgoing.value?.let { Box(Modifier.alpha(1f - fade.value)) { content(it) } }
        Box(Modifier.alpha(fade.value)) { content(shown.value) }
    }
}
