package org.viptv.app.features.settings

import android.Manifest
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.viptv.app.smartcast.SmartCastDiscovery
import org.viptv.app.smartcast.SmartCastNative
import org.viptv.app.smartcast.VizioCandidatesResult
import org.viptv.app.smartcast.VizioRequestResult
import org.viptv.app.smartcast.VizioResponseResult
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioPlatformSupport
import org.viptv.engine.wire.VizioTransportSupport
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class VizioSetupEngineLocalNetworkTest {
    private class FakeNative(
        val recordSubnets: MutableList<String> = mutableListOf(),
    ) : SmartCastNative {
        override fun discoveryCandidates(subnet: String): VizioCandidatesResult {
            recordSubnets += subnet
            return VizioCandidatesResult.Ok(emptyList())
        }
        override fun deviceinfoName(body: String) = "Vizio TV"
        override fun platformSupport(platform: String) = VizioPlatformSupport(true, VizioTransportSupport.NATIVE, "ok")
        override fun planRequest(operation: String, input: String) =
            VizioRequestResult.Err(VizioFailure(VizioFailureKind.INVALIDINPUT, "no", false, null))
        override fun parseResponse(status: Int, body: String, allowStatusless: Boolean) =
            VizioResponseResult.Err(VizioFailure(VizioFailureKind.INVALIDINPUT, "no", false, null))
    }

    private val application = RuntimeEnvironment.getApplication()

    private fun wiredWifi(): ConnectivityManager {
        val manager = application.getSystemService(ConnectivityManager::class.java)
        val network = manager.allNetworks.firstOrNull {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: error("no Wi-Fi network registered by Robolectric")
        val capabilities = NetworkCapabilities()
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
        val address = ReflectionHelpers.callConstructor(
            LinkAddress::class.java,
            ReflectionHelpers.ClassParameter.from(InetAddress::class.java, InetAddress.getByName("192.168.1.42")),
            ReflectionHelpers.ClassParameter.from(Integer.TYPE, 24),
        )
        shadowOf(manager).setLinkProperties(network, LinkProperties().apply { setLinkAddresses(listOf(address)) })
        return manager
    }

    private fun engine(discovery: SmartCastDiscovery) = AndroidVizioSetupEngine(application, discovery)

    private fun discovery(recordSubnets: MutableList<String> = mutableListOf()) = SmartCastDiscovery(FakeNative(recordSubnets))

    @Config(sdk = [36])
    @Test fun ordinaryInternetWifiReachesDiscoveryWithoutLocalNetworkClassification() {
        wiredWifi()
        val subnets = mutableListOf<String>()
        val engine = engine(discovery(subnets))

        runBlocking { engine.search(16) { throw AssertionError("no candidates expected") } }

        assertEquals(listOf("192.168.1"), subnets)
    }

    @Config(sdk = [36])
    @Test fun manualProbeReachesDiscoveryBelowThePermissionApi() {
        wiredWifi()
        val probed = mutableListOf<String>()
        val engine = engine(SmartCastDiscovery(FakeNative()) { origin -> probed += origin; null })

        val result = runBlocking { engine.probe("192.168.1.20") }

        assertEquals(null, result)
        assertEquals(listOf("https://192.168.1.20:7345"), probed)
    }

    @Config(sdk = [37])
    @Test fun grantedLocalNetworkPermissionReachesDiscovery() {
        wiredWifi()
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
        val subnets = mutableListOf<String>()
        val engine = engine(discovery(subnets))

        runBlocking { engine.search(16) { throw AssertionError("no candidates expected") } }

        assertEquals(listOf("192.168.1"), subnets)
    }

    @Config(sdk = [37])
    @Test fun deniedLocalNetworkPermissionThrowsNoAccessBeforeDiscovery() {
        wiredWifi()
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
        val subnets = mutableListOf<String>()
        val engine = engine(discovery(subnets))

        assertFailsWith<VizioNoAccess> { runBlocking { engine.search(16) { } } }
        assertEquals(emptyList(), subnets)
    }

    @Config(sdk = [37])
    @Test fun deniedLocalNetworkPermissionBlocksManualProbe() {
        wiredWifi()
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_LOCAL_NETWORK)
        val engine = engine(discovery())

        assertFailsWith<VizioNoAccess> { runBlocking { engine.probe("192.168.1.20") } }
    }
}