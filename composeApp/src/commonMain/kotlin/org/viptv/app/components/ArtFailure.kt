package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.network.HttpException
import kotlinx.coroutines.delay

internal const val ART_ATTEMPTS = 3
private const val ART_RETRY_MILLIS = 400L
private const val GONE_ART_LIMIT = 512

// A failed or cancelled image request is not a verdict: the surface keeps its no-art fallback and retries before settling on it.
internal class ArtFailure(url: String? = null) {
    private val knownGone = GoneArtwork.contains(url)
    var failed by mutableStateOf(knownGone)
        private set
    var gone by mutableStateOf(knownGone)
        private set
    var attempts by mutableIntStateOf(if (knownGone) ART_ATTEMPTS else 0)
        private set

    val settled: Boolean get() = attempts >= ART_ATTEMPTS

    fun retry() {
        if (attempts < ART_ATTEMPTS) attempts++
    }

    fun ready() {
        failed = false
        gone = false
    }

    // The same URL answers the same way, so a 4xx is final: settle now instead of repeating the failure.
    fun fail(throwable: Throwable?, url: String?) {
        failed = true
        if (throwable == null || !throwable.isFinalArtFailure()) return
        if (throwable.isGoneArtwork()) {
            gone = true
            GoneArtwork.record(url)
        }
        attempts = ART_ATTEMPTS
    }
}

private fun Throwable.isFinalArtFailure(): Boolean {
    val code = (this as? HttpException)?.response?.code ?: return false
    return code in 400..499 && code != 408 && code != 429
}

internal fun Throwable.isGoneArtwork(): Boolean {
    val code = (this as? HttpException)?.response?.code ?: return false
    return code == 404 || code == 410
}

@Composable
internal fun ArtFailure.retryAfterFailure(): Int {
    LaunchedEffect(failed, attempts) {
        if (!failed || attempts >= ART_ATTEMPTS) return@LaunchedEffect
        delay(ART_RETRY_MILLIS * (attempts + 1))
        retry()
    }
    return attempts
}

// A still that does not exist upstream is not asked for again: one 404 is one request for the session.
internal object GoneArtwork {
    private val urls = LinkedHashSet<String>()

    fun contains(url: String?): Boolean = url != null && url in urls

    fun record(url: String?) {
        if (url == null) return
        if (urls.size >= GONE_ART_LIMIT) urls.remove(urls.first())
        urls.add(url)
    }
}
