package org.viptv.app.connection

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformClient() = HttpClient(OkHttp) { configureTransport() }
actual fun browserOrigin(): String? = null
