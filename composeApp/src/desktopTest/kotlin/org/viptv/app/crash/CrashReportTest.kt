package org.viptv.app.crash

import kotlinx.serialization.json.Json
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private val reportJson = Json { encodeDefaults = true }

class CrashReportTest {
    @Test fun scrubKeepsHostAndMasksLongTokens() {
        assertEquals("https://host:8080", scrub("https://user:pass@host:8080/path?token=SECRET#t=10"))
        assertEquals("Torrentio", scrub("Torrentio"))
        assertNull(scrub(null))
        assertEquals("token <redacted> tail", scrub("token ABCDEF0123456789ABCDEF0123456789 tail"))
        assertEquals("ok abc123", scrub("ok abc123"))
    }

    @Test fun playbackAttemptKeepsOnlySourceMetadata() {
        val source = SourceCandidate(
            id = "s1",
            provider_id = "p",
            label = "Torrentio",
            quality_height = 1080,
            dynamic_range = listOf("hdr10"),
            presentation = SourcePresentation(container = "mkv", video_codec = "hevc", quality = "1080p", audio_codecs = listOf("aac", "ac3")),
        )
        val attempt = playbackAttempt(source, "gateway")
        assertEquals("s1", attempt.sourceId)
        assertEquals("Torrentio", attempt.providerLabel)
        assertEquals("mkv", attempt.container)
        assertEquals("hevc", attempt.videoCodec)
        assertEquals("1080p", attempt.resolution)
        assertEquals(listOf("hdr10"), attempt.hdr)
        assertEquals(listOf("aac", "ac3"), attempt.audioCodecs)
        assertEquals("gateway", attempt.delivery)
    }

    @Test fun sanitizedReportJsonHasNoQueryTokenOrHeaderValues() {
        val report = CrashReport(
            appVersion = "0.1.9+abc1234",
            buildCommit = "abc1234",
            deviceModel = "Pixel 8",
            androidVersion = "14",
            thread = "main",
            stack = "java.lang.RuntimeException: Failed to fetch https://user:pass@example.com/d/ABCDEF0123456789ABCDEF0123456789/file.mkv?token=SECRET",
            lastScreen = "Player",
            playback = playbackAttempt(SourceCandidate(id = "s1", provider_id = "p", label = "Torrentio"), "direct"),
        ).sanitized()
        val json = reportJson.encodeToString(CrashReport.serializer(), report)
        assertFalse(json.contains("SECRET"), json)
        assertFalse(json.contains("token="), json)
        assertFalse(json.contains("Bearer"), json)
        assertFalse(json.contains("Authorization"), json)
        assertFalse(json.contains("user:pass@"), json)
        assertFalse(json.contains("file.mkv"), json)
        assertFalse(json.contains("ABCDEF0123456789ABCDEF0123456789"), json)
    }
}