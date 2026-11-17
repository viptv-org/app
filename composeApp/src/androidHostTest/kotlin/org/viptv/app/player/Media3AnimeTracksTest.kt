package org.viptv.app.player

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList
import org.viptv.app.connection.ProfilePreferences
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Media3AnimeTracksTest {
    @get:Rule val folder = TemporaryFolder()

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Media3 never reached the expected state" }
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
    }

    @After fun forgetCodecs() { ShadowMediaCodecList.reset(); androidx.media3.exoplayer.mediacodec.MediaCodecUtil.clearDecoderInfoCache() }

    private fun decodeAac() = ShadowMediaCodecList.addCodec(MediaCodecInfoBuilder.newBuilder().setName("test.aac.decoder")
        .setCapabilities(MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 8000, 1))
            .setProfileLevels(arrayOf(MediaCodecInfo.CodecProfileLevel().apply { profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC })).build())
        .build())

    private fun play(preferences: ProfilePreferences, audio: String? = null, check: (List<PlaybackTrack>, String?) -> Unit) {
        val file = folder.newFile("anime.mkv").apply {
            writeBytes(requireNotNull(Media3AnimeTracksTest::class.java.getResourceAsStream("/subtitles/anime-signs-full.mkv")).readBytes())
        }
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val scope = CoroutineScope(Dispatchers.Main)
        val input = PlaybackInput("anime", Uri.fromFile(file).toString(), preferences = preferences)
        var reported = emptyList<PlaybackTrack>()
        val tracks = Media3Tracks(player, input) { fact -> if (fact is PlayerFact.Tracks) reported = fact.options.subtitles }
        val subtitles = Media3ProviderSubtitles(player, input, scope, tracks, Media3CaptionStyle(preferences), { 0L }) { _, _ -> }
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(audio)
                .setPreferredTextLanguage(preferences.preferred_subtitle_language).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, preferences.subtitle_mode != "always").build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            idleUntil { !player.currentTracks.isEmpty && (audio == null || player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) }
            subtitles.autoSelect(emptyList())
            val overrides = player.trackSelectionParameters.overrides.keys
            val chosen = player.currentTracks.groups.firstOrNull { it.mediaTrackGroup in overrides }?.getTrackFormat(0)?.label
            if (audio == null) idleUntil { overrides.isEmpty() || player.currentTracks.groups.any { it.mediaTrackGroup in overrides && it.isSelected } }
            tracks.changed()
            check(reported, chosen)
        } finally { subtitles.close(); scope.cancel(); player.release() }
    }

    @Test fun signsAndFullTracksKeepTheirNamesAndTheFullOneStarts() = play(ProfilePreferences(preferred_subtitle_language = "en", subtitle_mode = "always")) { tracks, _ ->
        assertEquals(listOf("Signs & Songs · English", "Full Subtitles · English"), tracks.map { it.label })
        assertEquals(listOf("Signs & Songs", "Full Subtitles"), tracks.map { it.name })
        assertEquals(listOf(true, false), tracks.map { it.forced })
        assertEquals("Full Subtitles", tracks.single { it.selected }.name)
    }

    @Test fun withNoSubtitleLanguageTheOnlyFullTrackStillWins() = play(ProfilePreferences(subtitle_mode = "always")) { tracks, _ ->
        assertTrue(tracks.isNotEmpty())
        assertEquals("Full Subtitles", tracks.single { it.selected }.name)
    }

    private val dubbing = ProfilePreferences(preferred_audio_language = "en", preferred_subtitle_language = "en", subtitle_mode = "foreign")

    @Test fun aDubStartsSignsAndSongs() { decodeAac(); play(dubbing, audio = "en") { _, chosen ->
        assertEquals("Signs & Songs", chosen)
    } }

    @Test fun foreignAudioStartsTheFullSubtitles() { decodeAac(); play(dubbing, audio = "ja") { _, chosen ->
        assertEquals("Full Subtitles", chosen)
    } }

    @Test fun aDubWithoutASignsTrackKeepsSubtitlesOff() { decodeAac(); play(dubbing.copy(preferred_subtitle_language = "es"), audio = "en") { _, chosen ->
        assertEquals(null, chosen)
    } }
}
