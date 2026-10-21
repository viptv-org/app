package org.viptv.experiment

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.playbackgateway.runtime.NativeRuntime
import org.viptv.app.connection.TorrentRuntimeChannel

/**
 * The only host of the shared torrent runtime: a non-exported, app-private
 * process (`:torrent_runtime`). One owner connects over a pipe pair; losing that
 * owner, or a runtime that cannot settle, ends the process and its media endpoints.
 */
class TorrentRuntimeService : Service() {
    private val connected = AtomicBoolean()
    private val worker = Executors.newSingleThreadExecutor()
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TorrentRuntimeChannel.CONNECT || Binder.getCallingUid() != Process.myUid() || reply == null || !connected.compareAndSet(false, true)) return false
            data.enforceInterface(TorrentRuntimeChannel.DESCRIPTOR)
            val incoming = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val outgoing = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            reply.writeNoException()
            reply.writeInt(Process.myPid())
            worker.execute {
                try {
                    DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(incoming)).use { input ->
                        DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(outgoing)).use { output ->
                            while (true) {
                                val request = TorrentRuntimeChannel.read(input).decodeToString()
                                val response = NativeRuntime.call(request)
                                TorrentRuntimeChannel.write(output, response.encodeToByteArray())
                                if (JSONObject(response).optBoolean("terminate") || JSONObject(request).optString("op") == "shutdown") break
                            }
                        }
                    }
                } catch (_: Exception) { /* Pipe death retires this process and every media capability. */ }
                finally { Process.killProcess(Process.myPid()) }
            }
            return true
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean { Process.killProcess(Process.myPid()); return false }
}
