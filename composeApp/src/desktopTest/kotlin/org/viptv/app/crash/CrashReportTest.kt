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
    @Test fun redactedStripsUserinfoQueryAndFragment() {
        assertEquals("https://host:8080/path", redacted("https://user:pass@host:8080/path?token=SECRET#t=10"))
        assertEquals("Torrentio", redacted("Torrentio"))
        assertNull(redacted(null))
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

    @Test fun reportJsonHasNoQueryTokenOrHeaderValues() {
        val source = SourceCandidate(
            id = "s1",
            provider_id = "p",
            label = "https://user:pass@example.com/stream.m3u8?token=SECRET#t=10",
            quality_height = 1080,
            dynamic_range = listOf("hdr10"),
            presentation = SourcePresentation(container = "mkv", video_codec = "hevc", quality = "1080p", audio_codecs = listOf("aac")),
        )
        val report = CrashReport(
            appVersion = "0.1.9+abc1234",
            buildCommit = "abc1234",
            deviceModel = "Pixel 8",
            androidVersion = "14",
            thread = "main",
            stack = "java.lang.RuntimeException: boom",
            lastScreen = "Player",
            playback = playbackAttempt(source, "direct"),
        )
        val json = reportJson.encodeToString(CrashReport.serializer(), report)
        assertFalse(json.contains("SECRET"), json)
        assertFalse(json.contains("token="), json)
        assertFalse(json.contains("Bearer"), json)
        assertFalse(json.contains("Authorization"), json)
        assertFalse(json.contains("user:pass@"), json)
        assertFalse(json.contains("@example.com"), json)
    }
}