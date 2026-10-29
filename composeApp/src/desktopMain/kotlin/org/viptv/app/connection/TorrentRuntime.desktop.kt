package org.viptv.app.connection

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import kotlinx.serialization.json.*
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal actual fun platformTorrentRuntime(): TorrentRuntimeHost? = DesktopTorrentHost.available

internal object DesktopTorrentHost : TorrentRuntimeHost {
    private val root: Path by lazy {
        val home = System.getProperty("user.home")
        val base = when {
            Platform.isWindows() -> System.getenv("LOCALAPPDATA") ?: home
            Platform.isMac() -> "$home/Library/Caches"
            else -> System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
        }
        Path.of(base, "viptv-experiment", "torrent-runtime")
    }
    private val executable: Path by lazy { unpackWorker(root) }
    val available: TorrentRuntimeHost? by lazy {
        try { elapsedMillis(); executable; this } catch (_: Exception) { null } catch (_: LinkageError) { null }
    }
    override fun elapsedMillis(): Long = DesktopTorrentClock.millis()
    override val cacheDirectory: String get() = root.resolve("pieces").toString()
    override fun connect(): TorrentRuntimePort = DesktopTorrentWorker(listOf(executable.toString()))

    private fun unpackWorker(root: Path): Path {
        val prefix = "torrent-runtime/${Platform.RESOURCE_PREFIX}/"
        val loader = javaClass.classLoader
        val manifest = loader.getResourceAsStream(prefix + "worker.json")?.use {
            val bytes = it.readNBytes(8193)
            check(bytes.size <= 8192)
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } ?: error("worker unavailable")
        check(manifest["protocol"]?.jsonPrimitive?.intOrNull == 2)
        check(manifest["suspend_aware_clock"]?.jsonPrimitive?.booleanOrNull == true)
        val hash = manifest.getValue("sha256").jsonPrimitive.content
        check(hash.matches(Regex("[a-f0-9]{64}")))
        val name = if (Platform.isWindows()) "torrent-worker.exe" else "torrent-worker"
        val directory = root.resolve("workers").resolve(hash)
        Files.createDirectories(directory)
        check(Files.isDirectory(directory, NOFOLLOW_LINKS))
        if (!Platform.isWindows()) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val destination = directory.resolve(name)
        fun digest(path: Path): String = Files.newInputStream(path).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!Files.isRegularFile(destination, NOFOLLOW_LINKS) || digest(destination) != hash) {
            val temporary = Files.createTempFile(directory, ".worker-", ".tmp")
            try {
                loader.getResourceAsStream(prefix + name)?.use { Files.copy(it, temporary, REPLACE_EXISTING) }
                    ?: error("worker unavailable")
                check(digest(temporary) == hash)
                if (!Platform.isWindows()) Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"))
                Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        check(Files.isExecutable(destination))
        return destination
    }
}

// These OS clocks include suspend; System.nanoTime is insufficient on Linux.
internal object DesktopTorrentClock {
    private val library by lazy { NativeLibrary.getInstance(if (Platform.isWindows()) "kernel32" else Platform.C_LIBRARY_NAME) }
    fun millis(): Long = when {
        Platform.isLinux() -> Memory((Native.LONG_SIZE * 2).toLong()).use { value ->
            check(library.getFunction("clock_gettime").invokeInt(arrayOf(7, value)) == 0) // CLOCK_BOOTTIME
            val seconds = value.getNativeLong(0).toLong()
            val nanos = value.getNativeLong(Native.LONG_SIZE.toLong()).toLong()
            check(seconds >= 0 && nanos in 0..999_999_999)
            Math.addExact(Math.multiplyExact(seconds, 1000), nanos / 1_000_000)
        }
        Platform.isWindows() -> library.getFunction("GetTickCount64").invokeLong(emptyArray()).also { check(it >= 0) }
        Platform.isMac() -> Memory(8).use { value ->
            check(library.getFunction("mach_timebase_info").invokeInt(arrayOf(value)) == 0)
            val numerator = value.getInt(0).toLong() and 0xffffffffL
            val denominator = value.getInt(4).toLong() and 0xffffffffL
            val ticks = library.getFunction("mach_continuous_time").invokeLong(emptyArray())
            check(ticks >= 0 && denominator > 0)
            (Math.multiplyExact(ticks / denominator, numerator) + ticks % denominator * numerator / denominator) / 1_000_000
        }
        else -> error("clock unavailable")
    }
}

internal class DesktopTorrentWorker(command: List<String>, readyTimeoutMillis: Long = 8_000, stderrLog: Path? = null) : TorrentRuntimePort {
    private val process = ProcessBuilder(command).redirectError(
        stderrLog?.let { ProcessBuilder.Redirect.to(Files.createFile(it).toFile()) }
            ?: ProcessBuilder.Redirect.DISCARD
    ).start()
    private val input = BufferedInputStream(process.inputStream)
    private val output = process.outputStream
    private val closed = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "torrent-runtime-control").apply { isDaemon = true } }
    private val shutdown = Thread({ terminate() }, "torrent-runtime-shutdown")
    init {
        try {
            Runtime.getRuntime().addShutdownHook(shutdown)
            val ready = timed(readyTimeoutMillis) { readReply() }
            check(ready["version"]?.jsonPrimitive?.intOrNull == 2 && ready["ready"]?.jsonPrimitive?.booleanOrNull == true)
        } catch (_: Exception) { terminate(); error("torrent worker unavailable") }
    }
    override fun call(command: JsonObject, timeoutMillis: Long): JsonObject {
        check(isAlive()) { "torrent worker unavailable" }
        val bytes = JsonObject(command + ("version" to JsonPrimitive(2))).toString().encodeToByteArray()
        require(bytes.size in 1..MAX_BYTES)
        val reply = timed(timeoutMillis) {
            output.write(bytes); output.write(10); output.flush()
            readReply().also { check(it["version"]?.jsonPrimitive?.intOrNull == 2) }
        }
        if (reply["terminate"]?.jsonPrimitive?.booleanOrNull == true) terminate()
        return reply
    }
    private fun readReply(): JsonObject {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) throw EOFException("torrent worker stopped")
            if (byte == 10) break
            check(buffer.size() < MAX_BYTES) { "torrent control limit" }
            buffer.write(byte)
        }
        return Json.parseToJsonElement(buffer.toByteArray().decodeToString(throwOnInvalidSequence = true)).jsonObject
    }
    private fun <T> timed(timeout: Long, effect: () -> T): T = try {
        executor.submit<T> { effect() }.get(timeout.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    } catch (_: Exception) { terminate(); error("torrent worker exchange failed") }
    override fun isAlive() = !closed.get() && process.isAlive
    internal fun processHasExited() = !process.isAlive
    internal fun exitCodeOrNull(): Int? = runCatching { process.exitValue() }.getOrNull()
    override fun terminate() {
        if (!closed.compareAndSet(false, true)) return
        // Kill before closing a pipe: an IO thread may hold its monitor while blocked.
        process.destroyForcibly()
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        runCatching { input.close() }; runCatching { output.close() }
        executor.shutdownNow()
        if (Thread.currentThread() != shutdown) runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
    }
    override fun toString() = "DesktopTorrentWorker(<redacted>)"
    private companion object { const val MAX_BYTES = 6 * 1024 * 1024 }
}
