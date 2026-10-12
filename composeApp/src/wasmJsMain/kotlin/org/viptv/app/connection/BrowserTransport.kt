package org.viptv.app.connection

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.window

actual fun platformClient() = HttpClient(Js) { configureTransport() }
actual fun browserOrigin(): String? = window.location.origin
