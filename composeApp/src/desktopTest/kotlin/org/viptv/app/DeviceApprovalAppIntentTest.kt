package org.viptv.app

import org.viptv.app.connection.deviceApprovalAppIntent
import kotlin.test.*

class DeviceApprovalAppIntentTest {
    @Test fun handoffKeepsTheCodeAndBrowserFallbackOnTheSameServer() {
        assertEquals(
            "intent://watch.example/device?code=00428701#Intent;scheme=https;package=org.viptv.experiment;S.browser_fallback_url=https%3A%2F%2Fwatch.example%2Fdevice%3Fcode%3D00428701;end",
            deviceApprovalAppIntent("https://watch.example", "00428701"),
        )
    }

    @Test fun invalidCodesAndUntrustedOriginsDoNotProduceAppIntents() {
        assertNull(deviceApprovalAppIntent("https://watch.example:18445", "00428701"))
        assertNull(deviceApprovalAppIntent("http://watch.example", "00428701"))
        assertNull(deviceApprovalAppIntent("https://watch.example/path", "00428701"))
        assertNull(deviceApprovalAppIntent("https://watch.example", "00428701#Intent;end"))
        assertNull(deviceApprovalAppIntent("https://watch.example", ""))
    }
}
