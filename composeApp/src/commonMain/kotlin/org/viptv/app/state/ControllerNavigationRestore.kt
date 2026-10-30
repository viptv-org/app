package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card

private const val NAVIGATION_SAVE_DELAY = 300L

private val rememberedScreens = setOf(Screen.Home, Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime,
    Screen.Search, Screen.Library, Screen.Details)

internal fun ApplicationState.navigationRecord(): SavedNavigation? {
    if (selectedProfile == null || screen !in rememberedScreens) return null
    val browse = browseQuery.copy(skip = 0)
    val library = libraryQuery.copy(offset = 0)
    return when (screen) {
        Screen.Details -> detail?.item?.let {
            SavedNavigation(screen.name, browse, searchTerm, library, it, detailReturnScreen.name)
        }
        else -> SavedNavigation(screen.name, browse, searchTerm, library)
    }
}

internal fun ApplicationController.rememberNavigation(browse: BrowseQuery? = null) {
    val state = mutable.value
    val profile = state.selectedProfile ?: return
    val snapshot = state.navigationRecord() ?: return
    val record = browse?.let { snapshot.copy(browse = it.copy(skip = 0)) } ?: snapshot
    if (record == rememberedNavigation && profile == rememberedNavigationProfile) return
    rememberedNavigation = record
    rememberedNavigationProfile = profile
    navigationSaveJob?.cancel()
    navigationSaveJob = scope.launch {
        delay(NAVIGATION_SAVE_DELAY)
        try { sessionStore.saveNavigation(profile, record) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }
}

internal fun ApplicationController.resumeNavigation() {
    if (mutable.value.selectedProfile == null || mutable.value.screen != Screen.Home) return
    navigationRestoreJob = scope.launch {
        val profile = mutable.value.selectedProfile ?: return@launch
        val record = try { sessionStore.navigation(profile) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null } ?: return@launch
        applyNavigationRecord(record, profile)
    }
}

private fun ApplicationController.applyNavigationRecord(saved: SavedNavigation, profile: String) {
    val target = rememberedScreens.firstOrNull { it.name == saved.screen } ?: return
    if (target == Screen.Home) return
    if (mutable.value.selectedProfile != profile || mutable.value.screen != Screen.Home) return
    saved.browse?.let { updateState { next -> next.copy(browseQuery = it.copy(skip = 0)) } }
    if (saved.search.isNotBlank()) updateState { next -> next.copy(searchTerm = saved.search) }
    saved.library?.let { updateState { next -> next.copy(libraryQuery = it.copy(offset = 0)) } }
    when (target) {
        Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime -> navigate(target, record = false, browse = saved.browse)
        Screen.Search -> {
            navigate(Screen.Search, record = false)
            if (saved.search.isNotBlank()) search(saved.search)
        }
        Screen.Library -> navigate(Screen.Library, record = false)
        Screen.Details -> saved.detail?.let { restoreDetails(it, screenNamed(saved.detailReturn)) }
        else -> Unit
    }
}

private fun screenNamed(name: String?): Screen = Screen.entries.firstOrNull { it.name == name } ?: Screen.Home

private fun ApplicationController.restoreDetails(card: Card, returnScreen: Screen) {
    val profile = mutable.value.selectedProfile ?: return
    beginDetails()
    updateState { next -> next.copy(detailReturnScreen = returnScreen, navBack = emptyList()) }
    val navigation = navigationGeneration
    val remote = connection
    val device = local
    scope.launch {
        val detail = try { device?.detail(profile, card) ?: requireNotNull(remote).detail(card) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (error is RemoteFailure && error.status == 401 && device == null) { expireSession(remote, device); return@launch }
            null
        }
        if (navigationGeneration != navigation || mutable.value.screen != Screen.Details ||
            mutable.value.selectedProfile != profile || connection !== remote || local !== device) return@launch
        if (detail == null) {
            updateState { next -> next.copy(screen = Screen.Home, busy = false, error = null, detail = null,
                detailHistory = emptyList(), navBack = emptyList(), playTapTitle = null, detailFailure = null) }
            return@launch
        }
        updateState { next -> next.copy(detail = detail.withCardArt(card)) }
    }
}
