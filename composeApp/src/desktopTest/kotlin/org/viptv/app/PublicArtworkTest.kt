package org.viptv.app

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import org.viptv.app.images.blurredArtwork
import org.viptv.app.images.publicArtworkUrl
import org.viptv.app.images.sizedArtwork
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

    // app#115: the server's own wsrv.nl URLs are sized for the slot too, not passed through raw.
    @Test fun serverProxiedArtIsSizedForTheSlot() {
        val inner = "https://simkl.in/posters/20/204275190b49d24215_m.webp"
        val sized = Url(assertNotNull(sizedArtwork("https://wsrv.nl/?url=$inner&q=90", 341, 512)))
        assertEquals("wsrv.nl", sized.host)
        assertEquals(inner, sized.parameters["url"])
        assertEquals("341", sized.parameters["w"])
        assertEquals("512", sized.parameters["h"])
        assertEquals("cover", sized.parameters["fit"])
        assertNull(sized.parameters["blur"])
        val encoded = Url(assertNotNull(sizedArtwork("https://wsrv.nl/?url=${inner.encodeURLParameter()}", 341, 512)))
        assertEquals(inner, encoded.parameters["url"])
    }

    // app#115: Simkl's 600 px `_w` fanart (and its other small sizes) become its 1920 px `medium`.
    @Test fun smallSimklSourcesAskForTheLargestSizeOfTheirShape() {
        fun inner(url: String) = Url(assertNotNull(sizedArtwork(url, 1280, 720))).parameters["url"]
        assertEquals("https://simkl.in/fanart/20/20178851e05e4c247e_medium.webp",
            inner("https://wsrv.nl/?url=https://simkl.in/fanart/20/20178851e05e4c247e_w.webp"))
        assertEquals("https://simkl.in/fanart/20/20178851e05e4c247e_medium.webp",
            inner("https://simkl.in/fanart/20/20178851e05e4c247e_mobile.webp"))
        assertEquals("https://simkl.in/posters/20/204275190b49d24215_m.webp",
            inner("https://simkl.in/posters/20/204275190b49d24215_c.webp"))
        assertEquals("https://simkl.in/episodes/12/abcdef_w.webp", inner("https://simkl.in/episodes/12/abcdef_m.webp"))
        // Already the largest: unchanged. Simkl's `_w` poster is a 600 x 338 landscape crop, never a poster upgrade.
        assertEquals("https://simkl.in/posters/20/204275190b49d24215_m.webp",
            inner("https://simkl.in/posters/20/204275190b49d24215_m.webp"))
        assertEquals("https://simkl.in/posters/20/204275190b49d24215_w.webp",
            inner("https://simkl.in/posters/20/204275190b49d24215_w.webp"))
    }

    @Test fun artTheServiceCannotFetchStaysUnchanged() {
        listOf("https://192.168.88.14:18451/api/images/simkl/posters/a_m.webp", "https://custom-addon.example/poster",
            "https://wsrv.nl/?w=10", "https://wsrv.nl/?url=/api/images/simkl/posters/a_m.webp")
            .forEach { assertEquals(it, sizedArtwork(it, 341, 512)) }
        assertNull(sizedArtwork(" ", 341, 512))
    }

    // app#80: a soft backdrop from server-proxied art really carries the service blur.
    @Test fun serverProxiedBackdropsAreBlurredByTheService() {
        val inner = "https://simkl.in/episodes/12/abcdef_w.webp"
        val blurred = Url(assertNotNull(blurredArtwork("https://wsrv.nl/?url=$inner", 1280, 720, blur = 12)))
        assertEquals(inner, blurred.parameters["url"])
        assertEquals("12", blurred.parameters["blur"])
        assertEquals("1280", blurred.parameters["w"])
        assertEquals("12", Url(assertNotNull(blurredArtwork("https://image.tmdb.org/t/p/original/a.jpg", 1280, 720, 12)))
            .parameters["blur"])
    }

    @Test fun backdropsTheServiceCannotBlurAskForTheRuntimeBlur() {
        listOf(null, "", "https://custom-addon.example/background", "https://wsrv.nl/?w=10",
            "/api/images/simkl/fanart/a_medium.webp").forEach { assertNull(blurredArtwork(it, 1280, 720, 12), "$it") }
    }
}
