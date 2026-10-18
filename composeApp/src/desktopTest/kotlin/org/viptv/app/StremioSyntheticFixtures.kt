package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.connection.*

/**
 * Synthetic server responses shaped like a large real Stremio account: two dozen add-ons with
 * repeated names and manifests, unicode and punctuation in names, unavailable transports, the
 * add-on stage's legacy `summary`/`warnings`, and thousands of mostly-episode review rows with
 * explicit nulls. Every name and handle is invented.
 */
internal object StremioSyntheticFixtures {
    private fun handle(prefix: String, index: Int) = "${prefix}_${index.toString().padStart(12, '0')}"
    private val addonNames = listOf("Cinemeta", "Synthetic Torrent", "Synthetic Torrent", "Synthetic Torrent TB", "MultiStream | Synthetic",
        "MultiStream [Backup]", "Subtitles+", "vSynthetic ⚡", "OpenSubtitles v3", "Anime Kitsu", "Synthetic Catalogs", "Trakt-ish",
        "Synthetic | Streams", "Mirror [EU]", "Mirror [US]", "Local Files", "Watch Hub", "Synthetic Debrid", "Search+", "Ñandú Meta",
        "Catalog • Plus", "Legacy http add-on", "Broken subtitles")
    private val resources = listOf(listOf("catalog", "meta"), listOf("stream"), listOf("stream"), listOf("stream"), listOf("stream", "catalog", "meta"),
        listOf("catalog", "stream", "meta"), listOf("subtitles"), listOf("stream", "meta", "catalog"), listOf("subtitles"), listOf("catalog", "meta", "subtitles"),
        listOf("catalog"), listOf("catalog", "meta", "subtitles"), listOf("catalog", "stream"), listOf("stream"), listOf("stream"), listOf("meta", "stream"),
        listOf("catalog", "stream"), listOf("stream"), listOf("stream", "catalog"), listOf("catalog", "meta"), listOf("subtitles"), listOf("meta", "stream"),
        listOf("subtitles"))
    val addonIds = addonNames.indices.map { handle("synthetic_addon", it) }
    val unavailableIds = setOf(addonIds[21], addonIds[22])

    fun addonsJson(profile: String = "7", expires: Long = stremioExpiry()): String = buildJsonObject {
        put("addons", JsonArray(addonNames.indices.map { i -> buildJsonObject {
            put("item_id", addonIds[i]); put("name", addonNames[i])
            put("reason", if (addonIds[i] in unavailableIds) JsonPrimitive("unsupported_transport") else JsonNull)
            put("resources", JsonArray(resources[i].map(::JsonPrimitive)))
            put("status", if (addonIds[i] in unavailableIds) "unavailable" else "add")
        } }))
        put("expires_at", expires); put("preview_id", handle("synthetic_preview", 1)); put("profile_id", profile.toInt())
        put("review_revision", 0); put("stage", "addons")
        put("summary", buildJsonObject { (stremioPreviewKeys + listOf("favorites_added", "progress_added", "progress_updated")).forEach { put(it, 0) } })
        put("warnings", JsonArray(listOf(JsonPrimitive("Synthetic legacy warning"))))
    }.toString()

    /** [rows] review rows: ~4% needs-review, ~9% My List titles, the rest dated or undated episode history. */
    fun reviewJson(rows: Int = 2_000, profile: String = "7", expires: Long = stremioExpiry(), revision: Int = 1): String {
        val shows = listOf("Synthetic Saga", "Ångström Chronicles", "Kitsu-Style Show: The Movie", "Long Title " + "Extended ".repeat(20),
            "Mystery [2019]", "Comedy | Night", "アニメ Synthetic", "Docu+ Series")
        val totals = mutableMapOf<String, Int>().withDefault { 0 }
        val items = (0 until rows).map { i ->
            val needsReview = i % 25 == 24
            val favorite = !needsReview && i % 11 == 0
            val movie = needsReview && i % 2 == 0 || favorite && i % 3 == 0
            val counts = buildJsonObject { stremioRowKeys.forEach { key ->
                val value = when (key) { "favorites_to_add" -> if (favorite) 1 else 0; "progress_to_add" -> if (!needsReview && !favorite) 1 else 0; else -> 0 }
                totals[key] = totals.getValue(key) + value; put(key, value)
            } }
            buildJsonObject {
                put("item_id", handle("synthetic_row", i))
                put("name", if (movie) "Synthetic Movie ${i / 7}" else shows[i % shows.size])
                put("type", if (movie) "movie" else "series")
                put("status", if (needsReview) "needs_review" else "ready")
                put("selectable", !needsReview)
                put("favorite_action", if (favorite) "add" else "none")
                put("progress_action", if (!needsReview && !favorite) "add" else "none")
                put("counts", counts)
                if (needsReview) put("reason", if (i % 50 == 49) "unverified_episode_bitfield" else "unmatched_identity")
                else {
                    val episode = !movie && !favorite
                    put("season", if (episode) JsonPrimitive(i / 3 % 22) else JsonNull)
                    put("episode", if (episode) JsonPrimitive(i % 168 + 1) else JsonNull)
                    val dated = i % 23 == 0
                    put("position", if (favorite) JsonNull else JsonPrimitive(if (dated) 1234.5 + i else 0.0))
                    put("duration", if (favorite) JsonNull else JsonPrimitive(if (dated) 9080.656 else 0.0))
                    put("watched", if (favorite) JsonNull else JsonPrimitive(!dated || i % 2 == 0))
                    put("watch_date_known", if (favorite) JsonNull else JsonPrimitive(dated))
                    put("completion_only", if (favorite) JsonNull else JsonPrimitive(!dated))
                    put("resume_active", if (favorite) JsonNull else JsonPrimitive(dated && i % 2 == 0))
                }
            }
        }
        return buildJsonObject {
            put("addons_to_add", 0); put("expires_at", expires); put("preview_id", handle("synthetic_preview", 1)); put("profile_id", profile.toInt())
            put("review_items", JsonArray(items)); put("review_revision", revision); put("stage", "review")
            put("summary", buildJsonObject {
                stremioRowKeys.forEach { put(it, totals.getValue(it)) }
                put("source_items", rows / 7); put("needs_review", rows / 200); put("skipped_items", rows / 20)
                listOf("favorites_added", "progress_added", "progress_updated").forEach { put(it, 0) }
            })
        }.toString()
    }
}
