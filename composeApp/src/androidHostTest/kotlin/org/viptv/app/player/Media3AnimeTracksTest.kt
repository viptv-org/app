package org.viptv.app.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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

    private fun play(preferences: ProfilePreferences, check: (List<PlaybackTrack>) -> Unit) {
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
                .setPreferredTextLanguage(preferences.preferred_subtitle_language).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            idleUntil { !player.currentTracks.isEmpty }
            subtitles.autoSelect(emptyList())
            idleUntil {
                val overrides = player.trackSelectionParameters.overrides.keys
                overrides.isEmpty() || player.currentTracks.groups.any { it.mediaTrackGroup in overrides && it.isSelected }
            }
            tracks.changed()
            check(reported)
        } finally { subtitles.close(); scope.cancel(); player.release() }
    }

    @Test fun signsAndFullTracksKeepTheirNamesAndTheFullOneStarts() = play(ProfilePreferences(preferred_subtitle_language = "en", subtitle_mode = "always")) { tracks ->
        assertEquals(listOf("Signs & Songs · English", "Full Subtitles · English"), tracks.map { it.label })
        assertEquals(listOf("Signs & Songs", "Full Subtitles"), tracks.map { it.name })
        assertEquals(listOf(true, false), tracks.map { it.forced })
        assertEquals("Full Subtitles", tracks.single { it.selected }.name)
    }

    @Test fun withNoSubtitleLanguageTheOnlyFullTrackStillWins() = play(ProfilePreferences(subtitle_mode = "always")) { tracks ->
        assertTrue(tracks.isNotEmpty())
        assertEquals("Full Subtitles", tracks.single { it.selected }.name)
    }
}
