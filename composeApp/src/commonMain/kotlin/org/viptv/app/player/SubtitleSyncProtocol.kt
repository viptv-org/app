package org.viptv.app.player

import kotlinx.serialization.json.*

internal data class SubtitleNativeEstimate(val epoch: Long, val revision: Long, val observationRevision: Long,
    val state: SubtitleSyncAvailability, val correction: SubtitleCorrection?)
internal data class SubtitlePcmResult(val epoch: Long, val status: String, val processedFrames: Long,
    val emittedFrames: Int, val estimate: SubtitleNativeEstimate?)

internal fun subtitlePcmBackendAvailable(value: String): Boolean {
    if (value.length > 4096) return false
    return runCatching {
        val item = Json.parseToJsonElement(value).jsonObject
        item["available"]?.jsonPrimitive?.booleanOrNull == true && item["workerOnly"]?.jsonPrimitive?.booleanOrNull == true &&
            item["encoding"]?.jsonPrimitive?.content == "pcmS16Le" && item["frameMs"]?.jsonPrimitive?.intOrNull == 20 &&
            item["maxInputBytes"]?.jsonPrimitive?.intOrNull == 32_768 &&
            item["frontendVersion"]?.jsonPrimitive?.content == "pcm16-webrtc20-v2" &&
            item["vadPatch"]?.jsonPrimitive?.content == "48k-input-offset-v1" &&
            item["vadRevision"]?.jsonPrimitive?.content == "e283ca41df3a84b0e87fb1f5cb9b21580a286b09" &&
            item["sampleRates"]?.jsonArray?.any { it.jsonPrimitive.intOrNull == 48_000 } == true &&
            item["channels"]?.jsonArray?.any { it.jsonPrimitive.intOrNull == 2 } == true
    }.getOrDefault(false)
}

internal fun subtitleCueJson(cues: List<SubtitleCueInterval>): String = buildJsonArray {
    require(cues.size <= 16_384)
    cues.forEach { cue -> add(buildJsonObject { put("startUs", cue.startUs); put("endUs", cue.endUs) }) }
}.toString().also { require(it.length <= 1024 * 1024) }

internal fun parseSubtitlePcmResult(value: String): SubtitlePcmResult {
    require(value.length <= 1024 * 1024)
    val json = Json.parseToJsonElement(value).jsonObject
    val epoch = requireNotNull(json["epoch"]?.jsonPrimitive?.longOrNull).also { require(it >= 0) }
    val status = requireNotNull(json["status"]?.jsonPrimitive?.contentOrNull)
    val processed = requireNotNull(json["processedFrames"]?.jsonPrimitive?.longOrNull).also { require(it >= 0) }
    val emitted = requireNotNull(json["emittedFrames"]?.jsonPrimitive?.intOrNull).also { require(it in 0..205) }
    val estimate = if (status != "ok") null else json["estimate"]?.takeUnless { it == JsonNull }?.jsonObject?.let { result ->
        require(result["algorithmVersion"]?.jsonPrimitive?.content == "activity-offset-affine-v1")
        require(result["confidenceKind"]?.jsonPrimitive?.content == "uncalibratedHeuristic")
        val state = when (result["state"]?.jsonPrimitive?.content) {
            "observing" -> SubtitleSyncAvailability.Observing
            "accepted" -> SubtitleSyncAvailability.Accepted
            "ambiguous" -> SubtitleSyncAvailability.Ambiguous
            "unavailable" -> SubtitleSyncAvailability.Unavailable
            else -> error("Unsupported subtitle estimate state")
        }
        val correction = result["correction"]?.takeUnless { it == JsonNull }?.jsonObject?.let { item ->
            SubtitleCorrection(requireNotNull(item["anchorUs"]?.jsonPrimitive?.longOrNull),
                requireNotNull(item["offsetUs"]?.jsonPrimitive?.longOrNull), requireNotNull(item["scale"]?.jsonPrimitive?.doubleOrNull))
        }
        val resultEpoch = requireNotNull(result["epoch"]?.jsonPrimitive?.longOrNull)
        require(resultEpoch == epoch)
        val revision = requireNotNull(result["revision"]?.jsonPrimitive?.longOrNull).also { require(it >= 0) }
        val observation = requireNotNull(result["observationRevision"]?.jsonPrimitive?.longOrNull).also { require(it >= 0) }
        require(state != SubtitleSyncAvailability.Accepted || correction != null)
        SubtitleNativeEstimate(resultEpoch, revision, observation, state, correction)
    }
    return SubtitlePcmResult(epoch, status, processed, emitted, estimate)
}

internal fun SubtitleSyncSession.acceptNativeEstimate(estimate: SubtitleNativeEstimate): Boolean =
    offer(estimate.epoch, estimate.revision, estimate.state, estimate.correction, estimate.observationRevision)
