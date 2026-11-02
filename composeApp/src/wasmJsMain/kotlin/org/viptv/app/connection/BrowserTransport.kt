@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.viptv.app.connection

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.sse.SSE
import kotlinx.browser.window

actual fun platformClient(): HttpClient = HttpClient(Js) {
    engine {
        configureRequest {
            cache = "no-store".toJsString()
            credentials = "same-origin".toJsString()
        }
    }
    // HttpCookies is deliberately not installed: its store reads document.cookie and cannot see the HttpOnly session cookie.
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000
    }
    install(SSE)
    followRedirects = false
}

actual fun browserOrigin(): String? = window.location.origin
