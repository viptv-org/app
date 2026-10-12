@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

private fun endTime(ms: Double): String = js("new Date(Date.now() + ms).toLocaleTimeString('en-US', {hour: 'numeric', minute: '2-digit'})")
internal actual fun playbackEndTime(remainingMillis: Long): String = endTime(remainingMillis.coerceAtLeast(0).toDouble())
