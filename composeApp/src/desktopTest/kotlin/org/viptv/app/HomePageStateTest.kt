package org.viptv.app

import org.viptv.app.components.ListPageState
import org.viptv.app.features.home.HOME_FAILED_TITLE
import org.viptv.app.features.home.HOME_NO_CATALOGS_MESSAGE
import org.viptv.app.features.home.HOME_NO_CATALOGS_TITLE
import org.viptv.app.features.home.HOME_NO_TITLES_MESSAGE
import org.viptv.app.features.home.HOME_NO_TITLES_TITLE
import org.viptv.app.features.home.HOME_RETRY_ACTION
import org.viptv.app.features.home.hasAnyRow
import org.viptv.app.features.home.homeFailureMessage
import org.viptv.app.features.home.homePageState
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePageStateTest {
    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "addon:1",
        title = "Title $id", badges = emptyList())

    private fun row(id: String = "movies", state: RowState = RowState.READY, cards: List<Card> = listOf(card("movies-1")),
        message: String? = null) = HomeRow(id = id, title = "Movies", provider_id = "addon:1", state = state, cards = cards,
        message = message)

    private fun home(complete: Boolean = true, hero: Card? = null, featured: List<Card> = emptyList(),
        rows: List<HomeRow> = emptyList()) =
        HomeSnapshot(Scope("viptv:remote", "account", "1", 1), 1, hero = hero, featured = featured, rows = rows, complete = complete)

    @Test fun theFirstAnswerBeforeThePageArrivesIsTheSkeleton() {
        assertEquals(ListPageState.Loading, homePageState(null, busy = true, error = null))
        assertEquals(ListPageState.Loading, homePageState(null, busy = false, error = null),
            "no page and no error is still a page on its way")
        assertEquals(ListPageState.Loading, homePageState(home(complete = false), busy = true, error = null),
            "a page that has not finished answering draws its rows, never the empty state")
    }

    @Test fun aFailedFirstRequestIsTheFailureState() {
        assertEquals(ListPageState.Failed, homePageState(null, busy = false, error = "The connection dropped."))
        assertEquals(ListPageState.Loading, homePageState(null, busy = true, error = "The connection dropped."),
            "the retry is in flight, so the state is the skeleton")
    }

    @Test fun anAnsweredPageWithNothingInItIsTheEmptyState() {
        assertEquals(ListPageState.Empty, homePageState(home(), busy = false, error = null))
        assertEquals(ListPageState.Empty, homePageState(home(rows = listOf(row(state = RowState.EMPTY, cards = emptyList()))),
            busy = false, error = null))
        assertEquals(ListPageState.Empty, homePageState(home(rows = listOf(row(state = RowState.FAILED, cards = emptyList()))),
            busy = false, error = null), "a catalog that answered nothing leaves the page empty")
    }

    @Test fun anyContentKeepsThePage() {
        assertEquals(ListPageState.Content, homePageState(home(hero = card("hero")), busy = false, error = null))
        assertEquals(ListPageState.Content, homePageState(home(featured = listOf(card("featured"))), busy = false, error = null))
        assertEquals(ListPageState.Content, homePageState(home(rows = listOf(row())), busy = false, error = null))
        assertEquals(ListPageState.Content, homePageState(home(rows = listOf(row(state = RowState.PENDING, cards = emptyList()))),
            busy = false, error = null), "a row still arriving is content the page draws")
    }

    @Test fun aFailedActionNeverBecomesThePageState() {
        assertEquals(ListPageState.Content, homePageState(home(hero = card("hero")), busy = false,
            error = "The connection dropped."), "the hero and the rows stay")
        assertEquals(ListPageState.Content, homePageState(home(featured = listOf(card("featured"))), busy = false,
            error = "Could not complete this action on this device."))
    }

    @Test fun theEmptyStateNamesCatalogsOrTitles() {
        assertFalse(home().hasAnyRow(), "no row at all is a profile with no catalog")
        assertTrue(home(rows = listOf(row(state = RowState.EMPTY, cards = emptyList()))).hasAnyRow(),
            "a catalog that answered nothing is not a missing catalog")
        assertEquals("The connection dropped.", homeFailureMessage(null, "The connection dropped."))
        assertEquals("The catalog timed out.", homeFailureMessage(home(rows = listOf(row(state = RowState.FAILED, cards = emptyList(), message = "The catalog timed out."))), null))
        assertEquals("", homeFailureMessage(home(), null), "no reason and no copy.md line means no body")
    }

    @Test fun theCopyIsTheDesignSystemsOwn() {
        assertEquals("No catalogs are available.", HOME_NO_CATALOGS_TITLE)
        assertEquals("Add or enable a catalog addon in Settings.", HOME_NO_CATALOGS_MESSAGE)
        assertEquals("No titles yet", HOME_NO_TITLES_TITLE)
        assertEquals("Your catalogs answered with nothing for this profile.", HOME_NO_TITLES_MESSAGE)
        assertEquals("Couldn’t load Home", HOME_FAILED_TITLE)
        assertEquals("Try again", HOME_RETRY_ACTION)
    }
}