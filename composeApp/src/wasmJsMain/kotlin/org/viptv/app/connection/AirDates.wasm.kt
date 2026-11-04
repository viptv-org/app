@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

internal actual fun airDateLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let { airDateOnly(it) } ?: airDate(atMillis.toDouble())

internal actual fun airDateWithYearLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let { airDateOnlyWithYear(it) } ?: airDateWithYear(atMillis.toDouble())

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

@JsFun("""(value) => {
 const parts = value.split('-').map(Number), d = new Date(parts[0], parts[1]-1, parts[2]);
 return d.toLocaleDateString(undefined, {month: 'short', day: 'numeric'});
}""")
private external fun airDateOnly(value: String): String

@JsFun("""(value) => {
 const parts = value.split('-').map(Number), d = new Date(parts[0], parts[1]-1, parts[2]);
 return d.toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'});
}""")
private external fun airDateOnlyWithYear(value: String): String