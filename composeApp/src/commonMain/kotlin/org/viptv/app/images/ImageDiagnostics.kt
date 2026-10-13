package org.viptv.app.images

import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.*
import kotlin.time.TimeSource

/** Optional QA timings contain no image URLs, tokens, or account data. */
internal class ImageDiagnostics(private val report: (String) -> Unit) : EventListener() {
    private val started = TimeSource.Monotonic.markNow()
    private var fetching = started
    private var decoding = started
    private var fetchMillis = 0L
    private var decodeMillis = 0L
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) { fetching = TimeSource.Monotonic.markNow() }
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) { fetchMillis = fetching.elapsedNow().inWholeMilliseconds }
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) { decoding = TimeSource.Monotonic.markNow() }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) { decodeMillis = decoding.elapsedNow().inWholeMilliseconds }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        report("total_ms=${started.elapsedNow().inWholeMilliseconds} fetch_ms=$fetchMillis decode_ms=$decodeMillis source=${result.dataSource} size=${result.image.width}x${result.image.height}")
    }
    override fun onError(request: ImageRequest, result: ErrorResult) { report("total_ms=${started.elapsedNow().inWholeMilliseconds} error=${result.throwable::class.simpleName}") }
}
