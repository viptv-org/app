package org.viptv.app.connection

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlinx.serialization.json.*

object AndroidTorrentRuntime {
    private var context: Context? = null
    private var service: Class<out Service>? = null
    private val available by lazy { supported() }

    fun attach(context: Context, service: Class<out Service>) {
        this.context = context.applicationContext; this.service = service
    }

    internal fun host(): TorrentRuntimeHost? = Host.takeIf { context != null && available }

    private object Host : TorrentRuntimeHost {
        override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
        override val cacheDirectory: String get() = File(requireNotNull(context).noBackupFilesDir, "torrent-runtime/pieces").path
        override fun connect(): TorrentRuntimePort = TorrentRuntimeWorker(requireNotNull(context), requireNotNull(service))
    }

    // The runtime ships for these process ABIs; Media3 must also reach its cleartext loopback endpoint.
    private fun supported(): Boolean = try {
        val app = requireNotNull(context).applicationInfo
        val abi = Build.SUPPORTED_ABIS.firstOrNull {
            when (it) { "armeabi-v7a" -> !Process.is64Bit(); "arm64-v8a", "x86_64" -> Process.is64Bit(); else -> false }
        }
        abi != null && NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1") &&
            (File(app.nativeLibraryDir, LIBRARY).isFile || ZipFile(app.sourceDir).use { it.getEntry("lib/$abi/$LIBRARY") != null })
    } catch (_: Exception) { false }

    private const val LIBRARY = "libtorrent_runtime_jni.so"
}

internal actual fun platformTorrentRuntime(): TorrentRuntimeHost? = AndroidTorrentRuntime.host()

// Length-prefixed frames carry control bodies outside Binder's small transaction buffer.
object TorrentRuntimeChannel {
    const val DESCRIPTOR = "org.viptv.app.private.TorrentRuntime"
    const val CONNECT = IBinder.FIRST_CALL_TRANSACTION
    private const val MAX_BYTES = 6 * 1024 * 1024
    fun read(input: DataInputStream): ByteArray {
        val size = input.readInt()
        require(size in 1..MAX_BYTES)
        return ByteArray(size).also(input::readFully)
    }
    fun write(output: DataOutputStream, bytes: ByteArray) {
        require(bytes.size in 1..MAX_BYTES)
        output.writeInt(bytes.size); output.write(bytes); output.flush()
    }
}

// A bound, owned worker process; terminating it revokes every media endpoint it served.
internal class TorrentRuntimeWorker(private val context: Context, service: Class<out Service>) : TorrentRuntimePort {
    private val connected = CountDownLatch(1)
    private val dead = CountDownLatch(1)
    private val closed = AtomicBoolean()
    @Volatile private var binder: IBinder? = null
    @Volatile private var pid = 0
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val io = Any()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            try { service.linkToDeath({ dead.countDown() }, 0) } catch (_: Exception) { dead.countDown() }
            connected.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName) { dead.countDown() }
        override fun onBindingDied(name: ComponentName) { dead.countDown(); connected.countDown() }
        override fun onNullBinding(name: ComponentName) { dead.countDown(); connected.countDown() }
    }

    init {
        check(context.bindService(Intent(context, service), connection, Context.BIND_AUTO_CREATE))
        try {
            check(connected.await(8, TimeUnit.SECONDS))
            val remote = requireNotNull(binder)
            val request = ParcelFileDescriptor.createPipe()
            val response = ParcelFileDescriptor.createPipe()
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(TorrentRuntimeChannel.DESCRIPTOR)
                request[0].writeToParcel(data, 0)
                response[1].writeToParcel(data, 0)
                check(remote.transact(TorrentRuntimeChannel.CONNECT, data, reply, 0))
                reply.readException()
                pid = reply.readInt().takeIf { it > 0 && it != Process.myPid() } ?: error("worker")
            } finally { data.recycle(); reply.recycle(); request[0].close(); response[1].close() }
            input = DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(response[0]))
            output = DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(request[1]))
        } catch (error: Exception) { terminate(); throw error }
    }

    override fun call(command: JsonObject, timeoutMillis: Long): JsonObject {
        check(!closed.get() && dead.count != 0L)
        val bytes = JsonObject(command + ("version" to JsonPrimitive(2))).toString().encodeToByteArray()
        val task = pipe.submit<JsonObject> {
            synchronized(io) {
                check(!closed.get())
                TorrentRuntimeChannel.write(requireNotNull(output), bytes)
                Json.parseToJsonElement(TorrentRuntimeChannel.read(requireNotNull(input)).decodeToString()).jsonObject
            }
        }
        val reply = try { task.get(timeoutMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS) }
            catch (error: Exception) { terminate(); throw error }
        if (reply["version"]?.jsonPrimitive?.intOrNull != 2) { terminate(); error("worker") }
        // A runtime that could not settle within its deadline must not serve again.
        if (reply["terminate"]?.jsonPrimitive?.booleanOrNull == true) terminate()
        return reply
    }

    override fun isAlive(): Boolean = !closed.get() && dead.count != 0L && binder?.isBinderAlive == true
    internal fun actualWorkerAlive(): Boolean = binder?.isBinderAlive == true

    // Only the PID returned by this private binding is ever killed.
    override fun terminate() {
        if (!closed.compareAndSet(false, true)) return
        if (pid > 0 && pid != Process.myPid() && binder?.isBinderAlive == true) Process.killProcess(pid)
        runCatching { output?.close() }; runCatching { input?.close() }
        runCatching { context.unbindService(connection) }
        val remote = binder ?: return
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (remote.isBinderAlive && dead.count != 0L && SystemClock.elapsedRealtime() < deadline) dead.await(10, TimeUnit.MILLISECONDS)
    }

    override fun toString() = "TorrentRuntimeWorker(<redacted>)"
    private companion object { val pipe = Executors.newCachedThreadPool { Thread(it, "torrent-runtime-pipe").apply { isDaemon = true } } }
}
