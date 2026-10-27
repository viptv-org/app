package org.viptv.app.updates

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One published Android build, as served at `<server origin>/updates/android/update.json`
 * (docs/APP_UPDATES.md). [url] is absolute after [parseUpdateFeed].
 */
@Serializable
data class UpdateFeed(
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val notes: String? = null,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Long? = null,
)

/**
 * The feed could not be read: malformed JSON, a missing field or a value the app refuses.
 * [missing]: the server publishes no feed at all (404, or its web app's HTML page for unknown paths).
 */
class UpdateFeedException(message: String, val missing: Boolean = false) : Exception(message)

/** Where the app looks for updates: the build's override, else the signed-in server; null in local mode. */
fun updateFeedUrl(serverOrigin: String?, override: String?): String? =
    override?.trim()?.takeIf { it.isNotEmpty() } ?: serverOrigin?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        ?.let { "$it$UPDATE_FEED_PATH" }

const val UPDATE_FEED_PATH = "/updates/android/update.json"

/** A sideloaded APK can be hundreds of megabytes; anything past this is a broken feed. */
private const val MAX_APK_BYTES = 1L shl 30
private val sha256Hex = Regex("[0-9a-fA-F]{64}")
private val feedJson = Json { ignoreUnknownKeys = true }

/**
 * Parses and validates [text] fetched from [feedUrl]. A relative `url` resolves against the
 * feed's address, so a server can publish the feed without knowing its public host.
 */
fun parseUpdateFeed(text: String, feedUrl: String): UpdateFeed {
    if (!text.trimStart().startsWith("{")) throw UpdateFeedException("No update feed at $feedUrl", missing = true)
    val feed = try {
        feedJson.decodeFromString(UpdateFeed.serializer(), text)
    } catch (error: SerializationException) {
        throw UpdateFeedException("Malformed update feed: ${error.message?.lineSequence()?.firstOrNull().orEmpty()}")
    } catch (error: IllegalArgumentException) {
        throw UpdateFeedException("Malformed update feed")
    }
    fun require(valid: Boolean, problem: String) { if (!valid) throw UpdateFeedException(problem) }
    require(feed.versionCode > 0, "Invalid version_code ${feed.versionCode}")
    require(feed.versionName.isNotBlank(), "Missing version_name")
    require(sha256Hex.matches(feed.sha256), "Invalid sha256")
    require(feed.size in 1..MAX_APK_BYTES, "Invalid size ${feed.size}")
    require(feed.minSupportedVersionCode == null || feed.minSupportedVersionCode > 0, "Invalid min_supported_version_code")
    require(feed.url.isNotBlank(), "Missing url")
    val absolute = resolveUpdateUrl(feedUrl, feed.url.trim())
    require(isAllowedUpdateUrl(absolute), "Update url must be https: ${feed.url}")
    return feed.copy(url = absolute, sha256 = feed.sha256.lowercase())
}

/**
 * HTTPS only; plain HTTP is allowed for loopback, the local test feed behind `adb reverse`. The
 * host comes from a URL parser, so userinfo (`http://localhost@evil.example/`) cannot fake it.
 */
internal fun isAllowedUpdateUrl(url: String): Boolean {
    val parsed = try { Url(url) } catch (_: Exception) { return false }
    // Ktor fills in defaults for strings that are not URLs; require the scheme to be really there.
    if (!url.lowercase().startsWith(parsed.protocol.name + "://") || parsed.host.isEmpty()) return false
    return when (parsed.protocol.name) {
        "https" -> true
        "http" -> parsed.host == "127.0.0.1" || parsed.host.equals("localhost", ignoreCase = true)
        else -> false
    }
}

internal fun resolveUpdateUrl(base: String, reference: String): String {
    if (Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(reference)) return reference
    val scheme = base.substringBefore("://", "")
    val rest = base.substringAfter("://")
    val origin = "$scheme://" + rest.substringBefore('/')
    return when {
        reference.startsWith("//") -> "$scheme:$reference"
        reference.startsWith("/") -> origin + reference
        else -> base.substringBefore('?').substringBeforeLast('/') + "/" + reference
    }
}
