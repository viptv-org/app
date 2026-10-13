@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

internal actual fun canonicalLanguageTag(value: String): String = normalizeLanguage(value)

@JsFun("""(value) => { try { return new Intl.Locale(value).toString().toLowerCase(); } catch { return value.toLowerCase(); } }""")
private external fun normalizeLanguage(value: String): String
