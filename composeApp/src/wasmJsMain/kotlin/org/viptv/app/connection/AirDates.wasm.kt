@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

internal actual fun airDateLabel(atMillis: Long): String = airDate(atMillis.toDouble())

internal actual fun airDateWithYearLabel(atMillis: Long): String = airDateWithYear(atMillis.toDouble())

@JsFun("""(value) => {
 const day = new Date(value);
 return day.toLocaleDateString(undefined, {month: 'short', day: 'numeric'});
}""")
private external fun airDate(value: Double): String

@JsFun("""(value) => {
 const day = new Date(value);
 return day.toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'});
}""")
private external fun airDateWithYear(value: Double): String