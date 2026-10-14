package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class IptvSetupCancellationTest {
    @Test fun cancellingDuringLocalOpenClosesItsLateConnectionWithoutSavingCredentials() = runBlocking {
        val fixture = IptvSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val controller = fixture.controller {
            entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS)); fixture.local
        }
        try {
            controller.connectIptv(fixture.path, IptvCredentials(IptvMode.M3u, "https://provider.example/list.m3u"))
            withTimeout(2000) { entered.await() }
            controller.cancelIptvEntry(); release.countDown()
            withTimeout(2000) { while (!fixture.closed) delay(10) }
            assertNull(controller.pendingIptv); assertNull(controller.local)
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertEquals(0, fixture.liveReads)
            NativeLocalConnection(fixture.path, fixture.secrets).use { reopened ->
                assertTrue(reopened.sourceIntegrationSchemas("default").connections.isEmpty())
            }
        } finally { release.countDown(); fixture.close(controller) }
    }
}
