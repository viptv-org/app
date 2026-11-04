package org.viptv.app.player

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidMediaSessionTest {
    private class PlayerStub(looper: Looper) : SimpleBasePlayer(looper) {
        val seeks = mutableListOf<Long>()
        var playing = false
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_IDLE)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            playing = playWhenReady; invalidateState(); return Futures.immediateVoidFuture()
        }
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            seeks += positionMs; return Futures.immediateVoidFuture()
        }
    }

    private class Controls {
        val targets = mutableListOf<Long>()
        val skips = mutableListOf<Long>()
    }

    private fun session(player: Player, controls: Controls) =
        AndroidMediaSession(RuntimeEnvironment.getApplication(), player, { controls.targets += it }, { controls.skips += it })

    @Test fun controllerSeekTakesThePlaybackControlPathNotThePlayer() {
        val player = PlayerStub(Looper.getMainLooper())
        val controls = Controls()
        session(player, controls).use { it.session.player.seekTo(42_000) }
        assertEquals(listOf(42_000L), controls.targets)
        assertTrue(player.seeks.isEmpty())
    }

    @Test fun controllerSkipTakesThePlaybackControlPath() {
        val player = PlayerStub(Looper.getMainLooper())
        val controls = Controls()
        session(player, controls).use { it.session.player.seekBack() }
        assertEquals(listOf(-10_000L), controls.skips)
        assertTrue(player.seeks.isEmpty())
    }

    @Test fun controllerSeekByMediaItemTakesThePlaybackControlPath() {
        val player = PlayerStub(Looper.getMainLooper())
        val controls = Controls()
        session(player, controls).use { it.session.player.seekTo(0, 7_000) }
        assertEquals(listOf(7_000L), controls.targets)
    }

    @Test fun playAndPauseReachTheOnePlayer() {
        val player = PlayerStub(Looper.getMainLooper())
        val controls = Controls()
        session(player, controls).use { session ->
            session.session.player.play()
            assertTrue(player.playing)
            session.session.player.pause()
            assertFalse(player.playing)
        }
        assertTrue(controls.targets.isEmpty())
    }

    @Test fun anActiveSessionIsPublishedForTheServiceAndWithdrawnWithThePlayer() {
        val media = session(PlayerStub(Looper.getMainLooper()), Controls())
        val application = RuntimeEnvironment.getApplication()
        AndroidMediaSessions.publish(application, media.session)
        assertNotNull(AndroidMediaSessions.session)
        AndroidMediaSessions.withdraw(application, media.session)
        assertNull(AndroidMediaSessions.session)
        media.close()
    }
}

private inline fun <T> AndroidMediaSession.use(block: (AndroidMediaSession) -> T): T =
    try { block(this) } finally { close() }
