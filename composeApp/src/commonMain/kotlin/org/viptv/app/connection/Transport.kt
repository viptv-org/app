package org.viptv.app.connection

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

expect fun platformClient(): HttpClient
expect fun browserOrigin(): String?

fun io.ktor.client.HttpClientConfig<*>.configureTransport() {
    install(HttpCookies)
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
    followRedirects = false
}
