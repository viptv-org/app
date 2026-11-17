package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.channels.FileChannel
import java.nio.channels.FileLockInterruptionException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

actual fun remoteSessionStore(): RemoteSessionStore = DesktopRemoteSessionStore()

internal class DesktopRemoteSessionStore(
    private val delegate: RemoteSessionStore = NativeRemoteSessionStore(remembersNavigation = true),
    private val lockPath: Path = desktopSessionLockPath(),
) : RemoteSessionStore by delegate {
    override suspend fun <T> withSessionLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = try { runInterruptible { channel.lock() } }
            catch (error: FileLockInterruptionException) {
                currentCoroutineContext().ensureActive()
                throw error
            }
            lock.use { block() }
        }
    }
}

internal fun desktopSessionLockPath(): Path {
    val user = System.getProperty("user.name").map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("")
    return Paths.get(System.getProperty("java.io.tmpdir"), "viptv-$user-session.lock")
}
