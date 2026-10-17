package org.viptv.app.connection

import io.ktor.client.*
import io.ktor.client.engine.js.*
import kotlinx.browser.window

actual fun platformClient(): HttpClient = HttpClient(Js) {
    // HttpCookies is deliberately NOT installed in the browser: the JS
    // engine's cookie storage reads document.cookie, which cannot see the
    // HttpOnly session cookie. The browser fetch cookie jar applies it.
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000
    }
    followRedirects = false
}

actual fun browserOrigin(): String? = window.location.origin
