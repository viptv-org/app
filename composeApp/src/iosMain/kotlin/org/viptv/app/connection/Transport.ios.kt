package org.viptv.app.connection

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import platform.Foundation.NSBundle

actual fun platformClient(): HttpClient = HttpClient(Darwin) {
    // HttpCookies is deliberately NOT installed on iOS: the Darwin engine's
    // cookie storage is shared with URLSession, but the session cookie is
    // HttpOnly and the backend's CSRF contract expects it to be applied
    // transparently, matching the browser's fetch cookie jar behavior.
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000
    }
    followRedirects = false
}

actual fun browserOrigin(): String? = null
