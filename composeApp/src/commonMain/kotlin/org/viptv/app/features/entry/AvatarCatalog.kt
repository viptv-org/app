package org.viptv.app.features.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.viptv.app.connection.Profile
import org.viptv.app.resources.Res


internal const val DEFAULT_AVATAR_STYLE = "critters"
private const val AVATAR_FILES = "files/avatars/"
private const val PACKAGE_IMAGES = "pkg:/images/"

internal data class AvatarPick(val style: String, val choice: Int)

internal data class AvatarWorld(val style: String, val name: String, val paths: List<String>) {
    val count get() = paths.size
}

internal class AvatarCatalog(val worlds: List<AvatarWorld>) {
    val total = worlds.sumOf { it.count }
    fun world(style: String): AvatarWorld? = worlds.firstOrNull { it.style == style }
    fun avatarPath(style: String, choice: Int): String? = world(style)?.paths?.getOrNull(choice - 1)
    fun avatarUri(style: String, choice: Int): String? = avatarPath(style, choice)?.let(Res::getUri)
}

@Serializable private data class CatalogFile(val per_category: Int, val categories: List<CatalogCategory>)
@Serializable private data class CatalogCategory(val style: String, val name: String, val items: List<CatalogItem>? = null)
@Serializable private data class CatalogItem(val local: String)

internal fun parseAvatarCatalog(text: String): AvatarCatalog {
    val file = Json { ignoreUnknownKeys = true }.decodeFromString<CatalogFile>(text)
    return AvatarCatalog(file.categories.map { category ->
        AvatarWorld(category.style, category.name, category.items?.map { AVATAR_FILES + it.local.removePrefix(PACKAGE_IMAGES) }
            ?: List(file.per_category) { "${AVATAR_FILES}avatar-catalog/${category.style}-${it + 1}.png" })
    })
}

internal sealed interface AvatarCatalogState {
    data object Loading : AvatarCatalogState
    data class Ready(val catalog: AvatarCatalog) : AvatarCatalogState
    data object Unavailable : AvatarCatalogState
}

private val loaded = MutableStateFlow<AvatarCatalog?>(null)
private val unavailable = MutableStateFlow(false)
private val loading = Mutex()

internal suspend fun loadAvatarCatalog(): AvatarCatalog = loaded.value ?: loading.withLock {
    loaded.value ?: parseAvatarCatalog(Res.readBytes("${AVATAR_FILES}avatar-catalog.json").decodeToString()).also { loaded.value = it }
}

@Composable
internal fun rememberAvatarCatalog(): AvatarCatalogState {
    val catalog by loaded.collectAsState()
    val failed by unavailable.collectAsState()
    LaunchedEffect(Unit) {
        if (loaded.value == null && !unavailable.value) try { loadAvatarCatalog() }
        catch (error: CancellationException) { throw error } catch (_: Exception) { unavailable.value = true }
    }
    return catalog?.let { AvatarCatalogState.Ready(it) } ?: if (failed) AvatarCatalogState.Unavailable else AvatarCatalogState.Loading
}

internal fun Profile.avatarPick(): AvatarPick? = when {
    avatarChoice != null -> AvatarPick(avatarStyle ?: DEFAULT_AVATAR_STYLE, avatarChoice)
    avatarStyle != null && avatarId == null && avatarUrl == null && avatar == null -> AvatarPick(DEFAULT_AVATAR_STYLE, 1)
    else -> null
}
