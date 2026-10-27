package org.viptv.app

import org.viptv.app.updates.*
import kotlin.test.*

class AppUpdateFeedTest {
    private val feedUrl = "https://viptv.example/updates/android/update.json"
    private val sha = "ab".repeat(32)
    private fun json(vararg fields: Pair<String, Any?>) = fields.filter { it.second != null }.joinToString(",", "{", "}") { (key, value) ->
        "\"$key\":" + if (value is String) "\"$value\"" else value.toString()
    }
    private val good = arrayOf("version_code" to 1301, "version_name" to "0.1.301+abc1234",
        "url" to "https://viptv.example/updates/android/viptv-1301.apk", "sha256" to sha, "size" to 123456)

    @Test fun aCompleteFeedParsesWithItsOptionalFields() {
        val feed = parseUpdateFeed(json(*good, "notes" to "Faster Home", "min_supported_version_code" to 1200), feedUrl)
        assertEquals(UpdateFeed(1301, "0.1.301+abc1234", "https://viptv.example/updates/android/viptv-1301.apk", sha, 123456,
            "Faster Home", 1200), feed)
    }

    @Test fun optionalFieldsMayBeMissingAndUnknownFieldsAreIgnored() {
        val feed = parseUpdateFeed(json(*good, "channel" to "dev"), feedUrl)
        assertNull(feed.notes); assertNull(feed.minSupportedVersionCode)
    }

    @Test fun malformedJsonIsRejected() {
        assertFalse(assertFailsWith<UpdateFeedException> { parseUpdateFeed("{\"version_code\": \"many\"}", feedUrl) }.missing)
        assertFalse(assertFailsWith<UpdateFeedException> { parseUpdateFeed("{\"version_code\": 1", feedUrl) }.missing)
    }

    @Test fun aServerThatAnswersWithItsWebPageHasNoFeed() {
        // The backend serves its web app's index.html for unknown paths.
        assertTrue(assertFailsWith<UpdateFeedException> { parseUpdateFeed("<!doctype html><html></html>", feedUrl) }.missing)
    }

    @Test fun eachRequiredFieldIsRequired() {
        for (missing in listOf("version_code", "version_name", "url", "sha256", "size")) {
            val fields = good.filter { it.first != missing }.toTypedArray()
            assertFailsWith<UpdateFeedException>("without $missing") { parseUpdateFeed(json(*fields), feedUrl) }
        }
    }

    @Test fun invalidValuesAreRejected() {
        fun with(key: String, value: Any) = json(*good.map { if (it.first == key) key to value else it }.toTypedArray())
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("sha256", "abc"), feedUrl) }
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("size", 0), feedUrl) }
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("version_code", 0), feedUrl) }
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("version_name", " "), feedUrl) }
        assertFailsWith<UpdateFeedException>("plain HTTP off loopback") {
            parseUpdateFeed(with("url", "http://viptv.example/viptv.apk"), feedUrl) }
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("url", "file:///sdcard/viptv.apk"), feedUrl) }
        assertFailsWith<UpdateFeedException>("userinfo cannot pose as loopback") {
            parseUpdateFeed(with("url", "http://localhost:1@evil.example/x.apk"), feedUrl) }
        assertFailsWith<UpdateFeedException> { parseUpdateFeed(with("url", "http://127.0.0.1.evil.example/x.apk"), feedUrl) }
    }

    @Test fun theChecksumIsNormalisedToLowercase() {
        val feed = parseUpdateFeed(json(*good.map { if (it.first == "sha256") "sha256" to sha.uppercase() else it }.toTypedArray()), feedUrl)
        assertEquals(sha, feed.sha256)
    }

    @Test fun aRelativeUrlResolvesAgainstTheFeed() {
        fun url(value: String) = parseUpdateFeed(json(*good.map { if (it.first == "url") "url" to value else it }.toTypedArray()), feedUrl).url
        assertEquals("https://viptv.example/updates/android/viptv-1301.apk", url("viptv-1301.apk"))
        assertEquals("https://viptv.example/files/viptv.apk", url("/files/viptv.apk"))
    }

    @Test fun loopbackHttpIsAllowedForTheLocalTestFeed() {
        val local = "http://127.0.0.1:8765/update.json"
        val feed = parseUpdateFeed(json(*good.map { if (it.first == "url") "url" to "viptv.apk" else it }.toTypedArray()), local)
        assertEquals("http://127.0.0.1:8765/viptv.apk", feed.url)
    }

    @Test fun theFeedLivesOnTheSignedInServerUnlessTheBuildOverridesIt() {
        assertEquals("https://viptv.example/updates/android/update.json", updateFeedUrl("https://viptv.example/", null))
        assertEquals("https://viptv.example/updates/android/update.json", updateFeedUrl("https://viptv.example", ""))
        assertEquals("https://github.example/latest/update.json", updateFeedUrl("https://viptv.example", "https://github.example/latest/update.json"))
        assertEquals("https://github.example/latest/update.json", updateFeedUrl(null, "https://github.example/latest/update.json"))
        assertNull(updateFeedUrl(null, ""), "Local mode without an override has no feed")
    }
}
