package org.viptv.app.connection

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** IMP-001 import previews. Every response is validated before any of it is shown. */
data class StremioAddon(val id: String, val name: String, val resources: List<String>, val status: String, val reason: String? = null)
data class StremioReviewItem(val id: String, val name: String, val type: String, val favoriteAction: String,
    val progressAction: String, val status: String, val counts: Map<String, Int>, val selectable: Boolean,
    val reason: String? = null, val season: Int? = null, val episode: Int? = null, val position: Double? = null,
    val duration: Double? = null, val watched: Boolean? = null, val watchDateKnown: Boolean? = null,
    val completionOnly: Boolean? = null, val resumeActive: Boolean? = null)
sealed interface StremioPreview { val previewId: String; val profileId: String; val expiresAt: Long }
data class StremioAddonPreview(override val previewId: String, override val profileId: String, override val expiresAt: Long,
    val addons: List<StremioAddon>) : StremioPreview
data class StremioReview(override val previewId: String, override val profileId: String, override val expiresAt: Long,
    val summary: Map<String, Int>, val items: List<StremioReviewItem>, val addonsToAdd: Int, val revision: Long) : StremioPreview
data class StremioCompletion(val profileId: String, val summary: Map<String, Int>, val alreadyCompleted: Boolean = false,
    val addonsAdded: Int? = null, val excludedItems: Int? = null)

/**
 * Server refusal. Only the fixed `error_code` and validated add-on handles from the JSON body
 * survive, whatever the HTTP status (the backend may use 424 for add-on/source failures).
 */
class StremioImportFailure(val status: Int, val code: String?, val failedAddons: List<String> = emptyList(),
    /** Optional fixed category per failed add-on handle (`addon_unreachable`, …); unknown codes are kept as codes only. */
    val failedReasons: Map<String, String> = emptyMap()) : Exception("The import request was refused.")
/** "Invalid review", "Invalid add-ons", "Replaced review" or "Invalid completion". */
class StremioInvalidResponse(kind: String) : Exception(kind)
/**
 * No usable import answer reached the client: a client timeout, a network failure, or a gateway
 * page (Cloudflare 502/524 HTML or text) instead of the server's JSON. During apply the outcome is unknown.
 */
class StremioTransportFailure(val timedOut: Boolean) : Exception(if (timedOut) "The import request timed out." else "The server could not be reached.")

/**
 * The server may verify metadata for ~34 s, remap through Simkl for ~25 s and back up before
 * applying; Cloudflare cuts origin requests at ~100 s. Only these requests get longer limits;
 * discard and every other endpoint keep the shared 15 s transport timeouts.
 */
internal data class StremioImportTimeouts(val preview: Long = 60_000, val review: Long = 95_000, val apply: Long = 95_000)

internal val stremioPreviewKeys = listOf("source_items", "favorites_to_add", "progress_to_add", "progress_to_update",
    "existing_preserved", "already_imported", "needs_review", "skipped_items")
internal val stremioCompletionKeys = listOf("favorites_added", "progress_added", "progress_updated", "existing_preserved",
    "already_imported", "needs_review", "skipped_items")
internal val stremioRowKeys = listOf("favorites_to_add", "progress_to_add", "progress_to_update", "existing_preserved", "already_imported")

internal interface StremioImportTransport {
    suspend fun preview(profile: String, email: String, password: String, library: Boolean, progress: Boolean): StremioPreview
    suspend fun review(profile: String, preview: String, addons: List<String>, expectedRevision: Long): StremioReview
    suspend fun apply(profile: String, preview: String, excluded: List<String>, revision: Long): StremioCompletion
    suspend fun discard(profile: String, preview: String)
}

@OptIn(ExperimentalTime::class)
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** One client for browser account sessions and native device sessions; the server decides add-on authority. */
internal class RemoteStremioImport(private val remote: RemoteConnection, private val now: () -> Long = ::epochMillis,
    private val timeouts: StremioImportTimeouts = StremioImportTimeouts()) : StremioImportTransport {
    private fun root(profile: String) = "/api/profiles/${profile.encodeURLPathPart()}/imports/stremio"

    override suspend fun preview(profile: String, email: String, password: String, library: Boolean, progress: Boolean): StremioPreview {
        val value = send("${root(profile)}/preview", HttpMethod.Post, buildJsonObject {
            put("email", email); put("password", password); put("import_library", library)
            put("import_progress", progress); put("inspect_addons", true)
        }, review = false, timeout = timeouts.preview) ?: throw StremioInvalidResponse("Invalid review")
        val stage = (value as? JsonObject)?.get("stage")?.text()
        return if (stage == "addons") parseAddons(value, profile, now()) ?: throw StremioInvalidResponse("Invalid add-ons")
        else parseReview(value, profile, now()) ?: throw StremioInvalidResponse("Invalid review")
    }

    override suspend fun review(profile: String, preview: String, addons: List<String>, expectedRevision: Long): StremioReview {
        val value = send("${root(profile)}/${preview.encodeURLPathPart()}/review", HttpMethod.Post, buildJsonObject {
            put("selected_addons", JsonArray(addons.sorted().map(::JsonPrimitive))); put("expected_review_revision", expectedRevision)
        }, review = true, timeout = timeouts.review)
        return parseReview(value, profile, now()) ?: throw StremioInvalidResponse("Invalid review")
    }

    override suspend fun apply(profile: String, preview: String, excluded: List<String>, revision: Long): StremioCompletion {
        val value = send("${root(profile)}/${preview.encodeURLPathPart()}/apply", HttpMethod.Post, buildJsonObject {
            put("confirm", true); put("excluded_items", JsonArray(excluded.sorted().map(::JsonPrimitive))); put("review_revision", revision)
        }, review = false, timeout = timeouts.apply)
        return parseCompletion(value, profile) ?: throw StremioInvalidResponse("Invalid completion")
    }

    override suspend fun discard(profile: String, preview: String) {
        send("${root(profile)}/${preview.encodeURLPathPart()}", HttpMethod.Delete, null, review = false, timeout = null)
    }

    private suspend fun send(path: String, method: HttpMethod, body: JsonElement?, review: Boolean, timeout: Long?): JsonElement? {
        val (status, text) = try { remote.exchange(path, method, body, timeout) }
        catch (error: CancellationException) {
            // Our own cancellation propagates; a request-timeout cancellation inside the client does not.
            currentCoroutineContext().ensureActive()
            if (!error.isTimeout()) throw error
            throw StremioTransportFailure(true)
        }
        catch (error: RemoteFailure) { throw StremioImportFailure(error.status, null) }
        // Browser fetch failures surface as kotlin.Error on Wasm, so every transport failure is classified here.
        catch (error: Throwable) { throw StremioTransportFailure(error.isTimeout()) }
        val value = try { text.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it) } } catch (_: Exception) { null }
        if (status !in 200..299) {
            val record = value as? JsonObject
            val code = record?.get("error_code")?.text()
            if (code == null && status in gatewayStatuses) throw StremioTransportFailure(status in timeoutStatuses)
            val failed = if (review && code == "stremio_addon_unavailable") safeHandles(record["failed_addon_items"]) else emptyList()
            throw StremioImportFailure(status, code, failed, safeReasons(record?.get("failed_addon_reasons"), failed))
        }
        return value
    }
}

private val timeoutStatuses = setOf(408, 504, 524)
/** Gateway and proxy failures that carry no import JSON (Cloudflare replaces 502 bodies with its own page). */
private val gatewayStatuses = timeoutStatuses + setOf(502, 503, 520, 521, 522, 523, 525, 526, 527, 530)
private fun Throwable.isTimeout(): Boolean = this is HttpRequestTimeoutException || this is SocketTimeoutException ||
    (this !is ConnectTimeoutException && cause?.let { it !== this && it.isTimeout() } == true)

private val tokenShape = Regex("^[A-Za-z0-9_-]{16,128}$")
private val controlCharacters = Regex("[\\u0000-\\u001f\\u007f]")
internal fun stremioToken(value: String?) = value != null && tokenShape.matches(value)
private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonElement?.count(): Int? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it in 0..100_000 }?.toInt()
private fun JsonElement?.absent() = this == null || this is JsonNull
private fun JsonElement?.flag(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
private fun JsonElement?.seconds(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..1_000_000.0 }
private fun safeName(value: String?) = value != null && value.isNotBlank() && value.length <= 512 && !controlCharacters.containsMatchIn(value) && !value.contains("://")
private fun safeHandles(value: JsonElement?): List<String> = (value as? JsonArray)?.takeIf { it.size <= 100 }
    ?.mapNotNull { it.text()?.takeIf(::stremioToken) }?.distinct().orEmpty()
private val reasonShape = Regex("^[a-z][a-z0-9_]{0,63}$")
/** Only code-shaped reasons for handles that were actually reported as failed. */
private fun safeReasons(value: JsonElement?, failed: List<String>): Map<String, String> = (value as? JsonObject)
    ?.filterKeys { it in failed }?.mapNotNull { (id, reason) -> reason.text()?.takeIf(reasonShape::matches)?.let { id to it } }?.toMap().orEmpty()
private fun counts(value: JsonElement?, keys: List<String>): Map<String, Int>? {
    val record = value as? JsonObject ?: return null
    return keys.associateWith { record[it].count() ?: return null }
}
private fun profileOf(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private class Session(val previewId: String, val profileId: String, val expiresAt: Long)
private fun session(value: JsonObject, profile: String, now: Long): Session? {
    val id = value["preview_id"]?.text()?.takeIf(::stremioToken) ?: return null
    if (profileOf(value["profile_id"]) != profile) return null
    val expires = (value["expires_at"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        ?.takeIf { it in 0..9_007_199_254_740_991 && it * 1000 > now } ?: return null
    return Session(id, profile, expires)
}

internal fun parseAddons(value: JsonElement?, profile: String, now: Long): StremioAddonPreview? {
    val record = value as? JsonObject ?: return null
    val base = session(record, profile, now) ?: return null
    if (record["stage"]?.text() != "addons") return null
    val rows = record["addons"] as? JsonArray ?: return null
    if (rows.size > 100) return null
    val ids = mutableSetOf<String>()
    val addons = rows.map { element ->
        val row = element as? JsonObject ?: return null
        val id = row["item_id"]?.text()?.takeIf { stremioToken(it) && ids.add(it) } ?: return null
        val name = row["name"]?.text()?.takeIf(::safeName) ?: return null
        val status = row["status"]?.text()?.takeIf { it in setOf("add", "existing", "unavailable") } ?: return null
        val resources = (row["resources"] as? JsonArray)?.takeIf { it.size <= 4 }?.map {
            it.text()?.takeIf { r -> r in setOf("catalog", "meta", "stream", "subtitles") } ?: return null
        } ?: return null
        StremioAddon(id, name, resources, status, row["reason"]?.text())
    }
    return StremioAddonPreview(base.previewId, base.profileId, base.expiresAt, addons)
}

internal fun parseReview(value: JsonElement?, profile: String, now: Long): StremioReview? {
    val record = value as? JsonObject ?: return null
    val base = session(record, profile, now) ?: return null
    if (record["stage"]?.text() != "review") return null
    val addonsToAdd = record["addons_to_add"].count() ?: return null
    val revision = (record["review_revision"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        ?.takeIf { it in 1..9_007_199_254_740_991 } ?: return null
    val summary = counts(record["summary"], stremioPreviewKeys) ?: return null
    val rows = record["review_items"] as? JsonArray ?: return null
    if (rows.size > 20_000) return null
    val actions = setOf("add", "update", "preserve", "already_imported", "none")
    val ids = mutableSetOf<String>()
    val items = rows.map { element ->
        val row = element as? JsonObject ?: return null
        val id = row["item_id"]?.text()?.takeIf { stremioToken(it) && ids.add(it) } ?: return null
        val name = row["name"]?.text()?.takeIf(::safeName) ?: return null
        val type = row["type"]?.text()?.takeIf { it in setOf("movie", "series") } ?: return null
        val status = row["status"]?.text()?.takeIf { it in setOf("ready", "preserved", "needs_review") } ?: return null
        val selectable = row["selectable"].flag() ?: return null
        val favorite = row["favorite_action"]?.text()?.takeIf { it in actions } ?: return null
        val progress = row["progress_action"]?.text()?.takeIf { it in actions } ?: return null
        val rowCounts = counts(row["counts"], stremioRowKeys) ?: return null
        var invalid = false
        fun <T> optional(key: String, read: (JsonElement?) -> T?): T? =
            if (row[key].absent()) null else read(row[key]) ?: null.also { invalid = true }
        val item = StremioReviewItem(id, name, type, favorite, progress, status, rowCounts, selectable, row["reason"]?.text(),
            optional("season") { it.count() }, optional("episode") { it.count() }, optional("position") { it.seconds() },
            optional("duration") { it.seconds() }, optional("watched") { it.flag() }, optional("watch_date_known") { it.flag() },
            optional("completion_only") { it.flag() }, optional("resume_active") { it.flag() })
        if (invalid) return null
        item
    }
    if (stremioRowKeys.any { key -> items.sumOf { it.counts.getValue(key).toLong() } != summary.getValue(key).toLong() }) return null
    return StremioReview(base.previewId, base.profileId, base.expiresAt, summary, items, addonsToAdd, revision)
}

internal fun parseCompletion(value: JsonElement?, profile: String): StremioCompletion? {
    val record = value as? JsonObject ?: return null
    if (profileOf(record["profile_id"]) != profile) return null
    val summary = counts(record["summary"], stremioCompletionKeys) ?: return null
    val added = if (record["addons_added"].absent()) null else record["addons_added"].count() ?: return null
    val excluded = if (record["excluded_items"].absent()) null else record["excluded_items"].count() ?: return null
    return StremioCompletion(profile, summary, record["already_completed"].flag() == true, added, excluded)
}
