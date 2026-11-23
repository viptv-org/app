package org.viptv.app.downloads

import org.junit.Test
import org.viptv.app.connection.ProfilePreferences
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.viptv.engine.wire.SubtitleCandidate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadSourcePolicyTest {
    @Test fun aSeasonStartedFromAManualPickSticksToItsBingeGroup() {
        val picked = source("picked", provider = "torrentio", binge = "group-a")
        val best = source("best", provider = "torrentio", binge = "group-b")
        val sameGroup = source("same-group", provider = "torrentio", binge = "group-a")
        val otherAddon = source("other-addon", provider = "other", binge = "group-a")
        assertEquals(listOf("same-group", "best", "other-addon"),
            downloadOrder(listOf(best, otherAddon, sameGroup), stale = null, release = picked).map { it.id })
        assertEquals(listOf("best", "other-addon", "same-group"),
            downloadOrder(listOf(best, otherAddon, sameGroup), stale = null, release = null).map { it.id })
    }

    @Test fun aRowMatchesADownloadByFingerprintThenProviderLabelAndSize() {
        val stored = source("old", fingerprint = "fp-1", label = "Show 1080p", size = 900)
        val byFingerprint = source("row-a", fingerprint = "fp-1", label = "Renamed", size = 1)
        val byLabel = source("row-b", fingerprint = null, label = "Show 1080p", size = 900)
        val other = source("row-c", label = "Show 720p", size = 400)
        assertEquals(mapOf("row-a" to "d1"), downloadedSourceIds(listOf(record("d1", stored)), listOf(other, byLabel, byFingerprint)))
        val unfingerprinted = stored.copy(source_fingerprint = null)
        assertEquals(mapOf("row-b" to "d1"), downloadedSourceIds(listOf(record("d1", unfingerprinted)), listOf(other, byLabel)))
        assertEquals(emptyMap(), downloadedSourceIds(listOf(record("d1", unfingerprinted)), listOf(other, byLabel.copy(presentation = null))))
        assertEquals(emptyMap(), downloadedSourceIds(listOf(record("d1", stored, DownloadState.Failed)), listOf(byFingerprint)))
    }

    @Test fun torrentAndStreamedSourcesAreBlockedWithAReason() {
        assertEquals(DOWNLOAD_TORRENT_ONLY, downloadBlockReason(source("t").copy(delivery = "torrent")))
        assertEquals(DOWNLOAD_NEEDS_CONVERSION, downloadBlockReason(source("h").copy(presentation = SourcePresentation(delivery_format = "HLS"))))
        assertNull(downloadBlockReason(source("f")))
    }

    @Test fun subtitlesFollowThePreferredLanguageAndAddSignsForDubbedAudio() {
        val candidates = listOf(
            SubtitleCandidate("fr", "subs", "fr", "French"),
            SubtitleCandidate("en-signs", "subs", "en", "English Signs & Songs"),
            SubtitleCandidate("en", "subs", "en", "English"),
        )
        val dubbed = source("dub").copy(audio_flags = listOf("dubbed"), audio_languages = listOf("en"))
        assertEquals(listOf("en"), downloadSubtitleChoice(candidates, ProfilePreferences(preferred_subtitle_language = "en"), dubbed).map { it.id })
        assertEquals(listOf("en", "en-signs"), downloadSubtitleChoice(candidates,
            ProfilePreferences(preferred_subtitle_language = "en", subtitle_mode = "foreign"), dubbed).map { it.id })
        assertEquals(listOf("en"), downloadSubtitleChoice(candidates,
            ProfilePreferences(preferred_subtitle_language = "en", subtitle_mode = "foreign"), source("sub").copy(audio_languages = listOf("ja"))).map { it.id })
        assertEquals(listOf("fr"), downloadSubtitleChoice(candidates, ProfilePreferences(preferred_subtitle_language = "fr"), dubbed).map { it.id })
    }

    @Test fun probedTracksKeepAudioAndSubtitlesWithKnownLanguages() {
        assertEquals("audio", downloadTrackKind("audio/mp4a-latm"))
        assertEquals("subtitle", downloadTrackKind("text/vtt"))
        assertEquals("subtitle", downloadTrackKind("application/x-subrip"))
        assertNull(downloadTrackKind("video/avc"))
        assertNull(downloadTrackLanguage("und"))
        assertEquals("jpn", downloadTrackLanguage("jpn"))
    }

    private fun source(id: String, provider: String = "addon", binge: String? = null, fingerprint: String? = null,
        label: String = id, size: Long? = 1000) =
        SourceCandidate(id, provider, label, binge_group = binge, source_fingerprint = fingerprint,
            presentation = SourcePresentation(size_bytes = size))

    private fun record(id: String, source: SourceCandidate, state: DownloadState = DownloadState.Completed) =
        DownloadRecord(id, "p", Card(MediaIdentity("tt9", "movie", emptyList()), provider_id = "catalog", title = "Movie", badges = emptyList()),
            state = state, source = source, addedAtMillis = 1)
}
