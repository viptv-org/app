@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

internal actual fun historyDayLabel(atMillis: Long): String = historyLabel(atMillis.toDouble())

@JsFun("""(value) => {
 const day = new Date(value), now = new Date();
 const ordinal = d => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000;
 const age = ordinal(now) - ordinal(day);
 if (age === 0) return 'Today';
 if (age === 1) return 'Yesterday';
 if (age >= 2 && age <= 6) return day.toLocaleDateString(undefined, {weekday: 'long'});
 if (age >= 7 && age <= 13) return 'Last week';
 return day.toLocaleDateString(undefined, {year:'numeric',month:'short',day:'numeric'});
}""")
private external fun historyLabel(value: Double): String
