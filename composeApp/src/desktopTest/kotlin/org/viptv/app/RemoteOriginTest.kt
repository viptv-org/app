package org.viptv.app

import org.viptv.app.connection.validatedRemoteOrigin
import kotlin.test.*

class RemoteOriginTest {
    @Test fun serverChoiceRetainsHttpsOriginWithoutPageOrCredentialDelimiters() {
        assertEquals("https://watch.example:18451", validatedRemoteOrigin(" https://watch.example:18451/ "))
        assertEquals("https://[::1]:18451", validatedRemoteOrigin("https://[::1]:18451/"))
        for (origin in listOf("http://watch.example", "https://person:secret@watch.example", "https://watch.example/device",
            "https://watch.example?", "https://watch.example/#", "https://[")) {
            assertFailsWith<IllegalArgumentException> { validatedRemoteOrigin(origin) }
        }
    }
}
