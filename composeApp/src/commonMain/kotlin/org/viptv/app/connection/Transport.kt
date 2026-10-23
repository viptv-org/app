package org.viptv.app.connection

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

expect fun platformClient(): HttpClient
expect fun browserOrigin(): String?

fun io.ktor.client.HttpClientConfig<*>.configureTransport() {
    install(HttpCookies)
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
    // Add-on endpoints legitimately redirect (Cinemeta moved every /meta and
    // /catalog call to cinemeta-live with 307s): a client that refuses to
    // follow them renders titles with no art, no episodes and a dead Play.
    followRedirects = true
}
