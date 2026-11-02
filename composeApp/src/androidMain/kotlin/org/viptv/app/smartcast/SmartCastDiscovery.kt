package org.viptv.app.smartcast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.viptv.engine.wire.VizioHttpMethod
import org.viptv.engine.wire.VizioRequest

data class VizioTv(val name: String, val origin: String)

class SmartCastDiscovery(
    private val native: SmartCastNative,
    private val probeOrigin: suspend (String) -> VizioTv?,
) {
    constructor(native: SmartCastNative) : this(native, defaultProbe(native))

    suspend fun search(subnet: String, resultLimit: Int = 16, onResult: (VizioTv) -> Unit = {}): List<VizioTv> {
        val candidates = (native.discoveryCandidates(subnet) as? VizioCandidatesResult.Ok)?.candidates ?: return emptyList()
        val found = mutableListOf<VizioTv>()
        val seen = mutableSetOf<String>()
        val publisher = Mutex()
        coroutineScope {
            val permits = Semaphore(32)
            for (candidate in candidates) {
                launch {
                    permits.withPermit {
                        val host = candidate.host.substringBefore(':')
                        val tv = try {
                            probeOrigin("https://${candidate.host}")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        } ?: return@withPermit
                        publisher.withLock {
                            if (seen.add(host) && found.size < resultLimit) {
                                found += tv
                                onResult(tv)
                            }
                        }
                    }
                }
            }
        }
        return found
    }

    suspend fun probe(origin: String): VizioTv? = probeOrigin(SmartCastTransport.canonicalOrigin(origin))

    companion object {
        fun defaultProbe(native: SmartCastNative): suspend (String) -> VizioTv? = { origin ->
            withContext(Dispatchers.IO) {
                try {
                    val canonical = SmartCastTransport.canonicalOrigin(origin)
                    val request = VizioRequest(
                        VizioHttpMethod.GET,
                        "$canonical/state/device/deviceinfo",
                        emptyMap(),
                        null,
                        2_500,
                        65_536,
                    )
                    val response = SmartCastTransport(canonical).execute(request)
                    if (response.status != 200) {
                        null
                    } else {
                        native.deviceinfoName(response.body)?.let { VizioTv(it, canonical) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}