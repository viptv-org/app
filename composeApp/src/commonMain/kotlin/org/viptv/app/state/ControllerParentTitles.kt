package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*

fun ApplicationController.openParentTitles(profile: String) {
    if (mutable.value.busy) return
    run {
        val selected = mutable.value.parentSettings?.profiles?.singleOrNull { it.id == profile }
            ?: throw LocalFailure("Refresh parental controls and choose a profile.")
        updateState { next -> next.copy(parentTitles = ParentTitlesState(profile, selected.name)) }
        parentTitlesRequest { remote, state ->
            val page = remote.approvedTitles(state.profile)
            state.copy(approved = page, loaded = true, nextOffset = if (page.size == 500) 500 else null)
        }
    }
}

fun ApplicationController.closeParentTitles() {
    job?.cancel(); generation++
    updateState { next -> next.copy(parentTitles = null, busy = false, error = null) }
}

fun ApplicationController.searchParentTitles(query: String) {
    if (mutable.value.busy) return
    run {
        val term = query.trim().take(200)
        if (term.isEmpty()) throw LocalFailure("Enter a title to search.")
        updateState { next -> next.copy(parentTitles = next.parentTitles?.copy(query = term, rows = emptyList(), searched = false, notice = null)) }
        parentTitlesRequest { remote, state -> state.copy(rows = remote.parentTitleSearch(state.profile, term).rows, searched = true) }
    }
}

fun ApplicationController.refreshApprovedTitles(more: Boolean = false) {
    if (mutable.value.busy) return
    run {
        parentTitlesRequest { remote, state ->
            val offset = if (more) state.nextOffset ?: return@parentTitlesRequest state else 0
            val page = remote.approvedTitles(state.profile, offset)
            state.copy(approved = (if (more) state.approved + page else page).distinctBy(::approvedTitleKey),
                loaded = true, nextOffset = if (page.size == 500) offset + page.size else null, notice = null)
        }
    }
}

fun ApplicationController.approveParentTitle(rowId: String, selected: org.viptv.engine.wire.Card) {
    if (mutable.value.busy) return
    run {
        parentTitlesRequest { remote, state ->
            val row = state.rows.singleOrNull { it.id == rowId } ?: throw LocalFailure("Search for the title again.")
            val item = row.items.singleOrNull { cardLookupKey(it.card) == cardLookupKey(selected) }
                ?: throw LocalFailure("Search for the title again.")
            if (!item.approval_supported) throw LocalFailure(ServerMessages.human(item.message, ServerMessages.PARENT_TITLE))
            val saved = remote.saveTitleApproval(state.profile, item.card.identity.kind, item.card.identity.id, true, row.provider_id, row.addon_id, item.card)
            val page = remote.approvedTitles(state.profile)
            state.copy(approved = (page + listOfNotNull(saved)).distinctBy(::approvedTitleKey), loaded = true,
                nextOffset = if (page.size == 500) 500 else null,
                notice = "Title approved for ${state.profileName}.")
        }
    }
}

fun ApplicationController.revokeParentTitle(selected: ApprovedTitle) {
    if (mutable.value.busy) return
    run {
        parentTitlesRequest { remote, state ->
            val stored = state.approved.singleOrNull { approvedTitleKey(it) == approvedTitleKey(selected) }
                ?: throw LocalFailure("Refresh the approved titles first.")
            if (stored.metadata_origin != null && stored.card == null)
                throw LocalFailure("Refresh this approval before removing it.")
            if (mutable.value.selectedProfile == state.profile) invalidatePolicyContent()
            remote.saveTitleApproval(state.profile, stored.card?.identity?.kind ?: stored.type, stored.card?.identity?.id ?: stored.id, false, expected = stored.card)
            val page = remote.approvedTitles(state.profile)
            state.copy(approved = page, loaded = true, nextOffset = if (page.size == 500) 500 else null,
                notice = "Approval removed. The profile’s age restrictions apply again.")
        }
    }
}

private suspend fun ApplicationController.parentTitlesRequest(action: suspend (RemoteConnection, ParentTitlesState) -> ParentTitlesState) {
    val remote = connection ?: throw LocalFailure("Connect to your account first.")
    if (!remote.accountManagementEnabled) throw LocalFailure("Manage approved titles through your server’s web dashboard.")
    val state = mutable.value.parentTitles ?: throw LocalFailure("Choose a profile first.")
    val owner = generation
    try {
        val result = action(remote, state)
        if (connection === remote && generation == owner && mutable.value.parentTitles?.profile == state.profile)
            updateState { next -> next.copy(parentTitles = result) }
    } catch (failure: RemoteFailure) {
        if (failure.status == 403) {
            val settings = try { remote.profileManagement() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (connection === remote && generation == owner)
                updateState { next -> next.copy(parentTitles = null, parentSettings = settings) }
            throw LocalFailure("Unlock parental controls and try again.")
        }
        if (failure.status == 404) throw LocalFailure("This server does not support title approvals. Update the server and try again.")
        if (failure.status == 409) throw LocalFailure("The profile or its integrations changed. Refresh and try again.")
        if (failure.status == 422) throw LocalFailure("This title cannot be approved through the selected integration.")
        throw failure
    }
}
