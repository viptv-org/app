package org.viptv.app.components

import org.viptv.engine.wire.Card
import kotlin.math.roundToInt

internal fun Card.metadataParts(): List<String> = metadata?.let { value -> buildList {
    value.year?.let { add(it.toString()) }
    value.runtime_minutes?.takeIf { it > 0 }?.let { add(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
    value.genres.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
    value.rating?.takeIf { it > 0 }?.let { add("★ ${(it * 10).roundToInt() / 10.0}") }
} }.orEmpty().ifEmpty { subtitle?.split('·')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty() }

internal fun Card.presentationBadges(): List<String> =
    (badges.filterNot { it.equals("Simkl", true) } + listOfNotNull(metadata?.certification)).distinct()
