@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

internal actual fun airDateLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let { airDateOnly(it, APP_DATE_LOCALE) } ?: airDate(atMillis.toDouble(), APP_DATE_LOCALE)

internal actual fun airDateWithYearLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let { airDateOnlyWithYear(it, APP_DATE_LOCALE) } ?: airDateWithYear(atMillis.toDouble(), APP_DATE_LOCALE)

@JsFun("""(value, locale) => {
 const day = new Date(value);
 return day.toLocaleDateString(locale, {month: 'short', day: 'numeric'});
}""")
private external fun airDate(value: Double, locale: String): String

@JsFun("""(value, locale) => {
 const day = new Date(value);
 return day.toLocaleDateString(locale, {month: 'short', day: 'numeric', year: 'numeric'});
}""")
private external fun airDateWithYear(value: Double, locale: String): String

@JsFun("""(value, locale) => {
 const parts = value.split('-').map(Number), d = new Date(parts[0], parts[1]-1, parts[2]);
 return d.toLocaleDateString(locale, {month: 'short', day: 'numeric'});
}""")
private external fun airDateOnly(value: String, locale: String): String

@JsFun("""(value, locale) => {
 const parts = value.split('-').map(Number), d = new Date(parts[0], parts[1]-1, parts[2]);
 return d.toLocaleDateString(locale, {month: 'short', day: 'numeric', year: 'numeric'});
}""")
private external fun airDateOnlyWithYear(value: String, locale: String): String