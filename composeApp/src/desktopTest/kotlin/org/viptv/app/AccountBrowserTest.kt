package org.viptv.app

import org.viptv.app.connection.LocalFailure
import org.viptv.app.connection.accountAuthorizationUri
import kotlin.test.*

class AccountBrowserTest {
    @Test fun nativeAuthorizationPreservesEncodedOAuthAndSupportsPrivateHttpsDashboards() {
        val url = "https://example.test/authorize?redirect_uri=https%3A%2F%2Fexample.test%2Freturn&state=a%2Bb%2520c#section"
        assertEquals(url, accountAuthorizationUri(url).toString())
        assertEquals("127.0.0.1", accountAuthorizationUri("https://127.0.0.1:18457/").host)
        assertEquals("[::1]", accountAuthorizationUri("https://[::1]:18457/").host)
    }
    @Test fun nativeAuthorizationRejectsNonWebHandlersCredentialsAndMalformedAuthorities() {
        for (url in listOf("javascript:alert(1)", "intent://example.test/", "file:///tmp/proof", "http://example.test/",
            "https://user:password@example.test/", "https:///missing-host", "https://example.test:99999/", "https://example.test/\n")) {
            val failure = assertFailsWith<LocalFailure> { accountAuthorizationUri(url) }
            assertFalse(failure.message.orEmpty().contains(url))
        }
    }
}
