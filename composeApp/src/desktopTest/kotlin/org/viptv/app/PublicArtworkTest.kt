package org.viptv.app

import io.ktor.http.Url
import org.viptv.app.images.publicArtworkUrl
import kotlin.test.*

class PublicArtworkTest {
    @Test fun publicLogosUseOneEncodedAlphaPreservingProxyAcrossPlatforms() {
        val original = "https://images.metahub.space/logo/medium/tt123/img"
        val result = Url(publicArtworkUrl(original))
        assertEquals("wsrv.nl", result.host)
        assertEquals(original, result.parameters["url"])
        assertEquals("webp", result.parameters["output"])
        assertNull(result.parameters["bg"])
        assertEquals(result.toString(), publicArtworkUrl(result.toString()))
    }
    @Test fun privateSignedAndLocalAssetsNeverLeaveTheirOriginalTransport() {
        listOf("https://192.168.88.14:18451/api/images/simkl/posters/a_m.webp",
            "/api/images/simkl/posters/a_m.webp", "file:///tmp/logo.png",
            "https://custom-addon.example/logo/token", "https://images.metahub.space/logo?token=private",
            "https://user:password@images.metahub.space/logo", "https://images.metahub.space:8443/logo",
            "https://images.metahub.space.evil.example/logo").forEach { assertEquals(it, publicArtworkUrl(it)) }
    }
}
