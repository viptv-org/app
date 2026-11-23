package org.viptv.app.downloads

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

object AndroidDownloads {
    internal var engine: DownloadEngine? = null
        private set

    fun attach(context: Context) {
        if (engine != null) return
        val application = context.applicationContext
        if (processName() != application.packageName) return
        val directory = File(application.getExternalFilesDir(null) ?: application.filesDir, "downloads")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val created = DownloadEngine(FileDownloadFiles(directory), HttpClient(OkHttp), networkState(application), scope)
        engine = created
        scope.launch {
            created.items.map { items -> items.any { it.state == DownloadState.Resolving || it.state == DownloadState.Downloading } }
                .distinctUntilChanged()
                .collect { busy ->
                    if (busy) runCatching { ContextCompat.startForegroundService(application, Intent(application, DownloadService::class.java)) }
                }
        }
    }

    private fun processName(): String =
        if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
        else File("/proc/self/cmdline").readText().trimEnd('\u0000')

    private fun networkState(context: Context): StateFlow<DownloadNetwork> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun kind(capabilities: NetworkCapabilities?): DownloadNetwork = when {
            capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> DownloadNetwork.None
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> DownloadNetwork.Unmetered
            else -> DownloadNetwork.Metered
        }
        val state = MutableStateFlow(kind(manager.activeNetwork?.let(manager::getNetworkCapabilities)))
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { state.value = kind(capabilities) }
            override fun onLost(network: Network) { state.value = DownloadNetwork.None }
        })
        return state
    }
}
