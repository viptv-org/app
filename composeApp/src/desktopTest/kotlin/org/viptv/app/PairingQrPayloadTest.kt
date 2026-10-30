package org.viptv.app

import org.viptv.app.connection.PairingQrPayload
import kotlin.test.*

class PairingQrPayloadTest {
    private val origin = "https://watch.example"

    @Test fun readsTheCodeFromTheServersPairingUrls() {
        assertEquals("00428701", PairingQrPayload.code("https://watch.example/device?code=00428701", origin))
        assertEquals("00428701", PairingQrPayload.code("https://watch.example/api/auth/device/qr?code=00428701", origin))
        assertEquals("00428701", PairingQrPayload.code("https://watch.example/device?code=00428701&state=browse", origin))
        assertEquals("00428701", PairingQrPayload.code("  https://watch.example/device?code=00428701\n", origin))
        assertEquals("00428701", PairingQrPayload.code("https://WATCH.example/device?code=00428701", origin))
    }

    @Test fun matchesTheServersOwnPort() {
        assertEquals("00428701", PairingQrPayload.code("https://tv.example:8443/device?code=00428701", "https://tv.example:8443"))
        assertEquals("00428701", PairingQrPayload.code("https://tv.example/device?code=00428701", "https://tv.example:443"))
        assertNull(PairingQrPayload.code("https://tv.example:8444/device?code=00428701", "https://tv.example:8443"))
    }

    @Test fun rejectsForeignHosts() {
        assertNull(PairingQrPayload.code("https://evil.example/device?code=00428701", origin))
        assertNull(PairingQrPayload.code("https://watch.example.evil.example/device?code=00428701", origin))
        assertNull(PairingQrPayload.code("https://watch.example@evil.example/device?code=00428701", origin))
        assertNull(PairingQrPayload.code("http://watch.example/device?code=00428701", origin))
    }

    @Test fun rejectsUrlsThatDoNotCarryAPairingCode() {
        assertNull(PairingQrPayload.code("https://watch.example/", origin))
        assertNull(PairingQrPayload.code("https://watch.example/device", origin))
        assertNull(PairingQrPayload.code("https://watch.example/browse?code=00428701", origin))
        assertNull(PairingQrPayload.code("https://watch.example/device?code=0123", origin))
        assertNull(PairingQrPayload.code("https://watch.example/device?code=0042870A", origin))
        assertNull(PairingQrPayload.code("https://watch.example/device?user_code=00428701", origin))
        assertNull(PairingQrPayload.code("viptv://device?code=00428701", origin))
        assertNull(PairingQrPayload.code("not a url", origin))
        assertNull(PairingQrPayload.code("", origin))
    }
}
