package org.viptv.app.features.home

import org.viptv.app.components.ListPageState
import org.viptv.app.components.listPageState
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.RowState

// copy.md ## Empty states — Home, in the "Couldn’t load …" form the other list pages use.
internal const val HOME_NO_CATALOGS_TITLE = "No catalogs are available."
internal const val HOME_NO_CATALOGS_MESSAGE = "Add or enable a catalog add-on in Settings."
internal const val HOME_NO_CATALOGS_ACTION = "Open settings"
internal const val HOME_NO_TITLES_TITLE = "No titles yet"
internal const val HOME_NO_TITLES_MESSAGE = "Your catalogs answered with nothing for this profile."
internal const val HOME_FAILED_TITLE = "Couldn’t load Home"
internal const val HOME_RETRY_ACTION = "Try again"

internal fun homePageState(home: HomeSnapshot?, busy: Boolean, error: String?): ListPageState =
    listPageState(home?.shownCount() ?: 0, busy, error, answered = home?.complete == true)

internal fun HomeSnapshot.shownCount(): Int = (if (hero != null) 1 else 0) + featured.size +
    rows.count { it.state == RowState.PENDING || (it.state == RowState.READY && it.cards.isNotEmpty()) }

// A failed action never reaches the page state; only the page's own load failure does.
internal fun homeFailureMessage(home: HomeSnapshot?, error: String?): String =
    error ?: home?.rows?.firstNotNullOfOrNull { it.message } ?: ""

internal fun HomeSnapshot.hasAnyRow(): Boolean = rows.isNotEmpty()