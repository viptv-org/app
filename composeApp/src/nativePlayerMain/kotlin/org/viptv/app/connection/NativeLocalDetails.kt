package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

internal suspend fun NativeLocalConnection.fetchDetailPage(profile: String, card: Card,
    offset: Int, limit: Int): DetailSnapshot {
    require(offset in 0..10_000 && limit in 1..128)
    val identity = card.parent_identity ?: card.identity
    val cached = withContext(Dispatchers.IO) {
        engine.detailPage(profile, identity.kind, identity.id, offset.toUInt(), limit.toUInt())
    }
    if (cached != null) return withContext(Dispatchers.IO) { json.decodeFromString(cached) }
    val plan = try { withContext(Dispatchers.IO) {
        engine.planDetail(profile, identity.kind, identity.id, card.provider_id)
    } } catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("This title is unavailable from your enabled add-ons.") }
    val operation = json.parseToJsonElement(plan).jsonObject
    val body = addonJson(client, operation.getValue("url").jsonPrimitive.content)
    return try { withContext(Dispatchers.IO) {
        json.decodeFromString(engine.projectDetailPage(operation.getValue("operation").jsonPrimitive.content,
            body, offset.toUInt(), limit.toUInt()))
    } } catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("The add-on returned invalid title details.") }
}
