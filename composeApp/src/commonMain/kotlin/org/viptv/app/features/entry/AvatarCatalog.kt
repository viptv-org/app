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

/*
 * The design ADM-004 avatar catalog, bundled under composeResources/files/avatars
 * from the design commit in AVATAR_CATALOG_REF. Generated worlds hold
 * `per_category` choices named avatar-catalog/{style}-{n}.png; character worlds
 * list their own items. A pick is a world `style` plus a stable 1-based `choice`,
 * the same pair the backend and the local engine store.
 */

/** Backend default world for a profile that never picked an avatar. */
internal const val DEFAULT_AVATAR_STYLE = "critters"
private const val AVATAR_FILES = "files/avatars/"
private const val PACKAGE_IMAGES = "pkg:/images/"

/** A catalog avatar: world [style] and 1-based [choice]. */
internal data class AvatarPick(val style: String, val choice: Int)

/** A catalog world; [paths] holds the Compose resource path of each choice in order. */
internal data class AvatarWorld(val style: String, val name: String, val paths: List<String>) {
    val count get() = paths.size
}

internal class AvatarCatalog(val worlds: List<AvatarWorld>) {
    val total = worlds.sumOf { it.count }
    fun world(style: String): AvatarWorld? = worlds.firstOrNull { it.style == style }
    /** Compose resource path of a pick, or null when the style or choice is not in the catalog. */
    fun avatarPath(style: String, choice: Int): String? = world(style)?.paths?.getOrNull(choice - 1)
    /** Loadable URI of a pick (for Coil), or null outside the catalog. */
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

private val loaded = MutableStateFlow<AvatarCatalog?>(null)
private val loading = Mutex()

/** Parses the bundled catalog once per process. */
internal suspend fun loadAvatarCatalog(): AvatarCatalog = loaded.value ?: loading.withLock {
    loaded.value ?: parseAvatarCatalog(Res.readBytes("${AVATAR_FILES}avatar-catalog.json").decodeToString()).also { loaded.value = it }
}

/** The bundled catalog, or null until it has loaded (or when it cannot be read). */
@Composable
internal fun rememberAvatarCatalog(): AvatarCatalog? {
    val catalog by loaded.collectAsState()
    LaunchedEffect(Unit) {
        if (loaded.value == null) try { loadAvatarCatalog() } catch (error: CancellationException) { throw error } catch (_: Exception) { }
    }
    return catalog
}

/**
 * The catalog avatar a profile shows, or null when it shows something else.
 * A saved pick wins over everything. Without one, a legacy bundled id or the
 * server's random-seed `avatarUrl` is shown; a local engine profile, which has
 * neither, shows the default `critters` #1.
 */
internal fun Profile.avatarPick(): AvatarPick? = when {
    avatarChoice != null -> AvatarPick(avatarStyle ?: DEFAULT_AVATAR_STYLE, avatarChoice)
    avatarStyle != null && avatarId == null && avatarUrl == null && avatar == null -> AvatarPick(DEFAULT_AVATAR_STYLE, 1)
    else -> null
}

/** Picker layouts: three columns of 15 on phones, six columns of 18 on TV and desktop. */
internal enum class AvatarPickerLayout(val columns: Int, val pageSize: Int) {
    Phone(3, 15), Desktop(6, 18), Television(6, 18)
}

/** Paging over a world of [count] avatars, [size] per page, wrapping at both ends. */
internal data class AvatarPaging(val count: Int, val size: Int) {
    val pages = maxOf(1, (count + size - 1) / size)
    fun next(page: Int) = (page + 1) % pages
    fun previous(page: Int) = (page - 1 + pages) % pages
    /** 1-based choices shown on [page]. */
    fun choices(page: Int): IntRange = (page * size + 1)..minOf(count, (page + 1) * size)
    fun pageOf(choice: Int) = ((choice - 1) / size).coerceIn(0, pages - 1)
}
