package org.viptv.app.state

import org.viptv.app.connection.*

private fun ApplicationController.xtreamRemote(): RemoteConnection =
    connection ?: throw LocalFailure("Xtream connections need a server account.")

internal suspend fun ApplicationController.loadXtreamConnections() {
    val remote = xtreamRemote()
    val owner = generation
    val connections = remote.xtreamConnections()
    val defaultLive = remote.xtreamDefaultLive()
    if (connection === remote && generation == owner)
        updateState { next -> next.copy(xtreamConnections = connections, xtreamDefaultLive = defaultLive, xtreamNotice = null) }
}

private suspend fun ApplicationController.publishXtreamDefaultLive(remote: RemoteConnection, owner: Long) {
    val defaultLive = remote.xtreamDefaultLive()
    if (connection === remote && generation == owner) updateState { next -> next.copy(xtreamDefaultLive = defaultLive) }
}

fun ApplicationController.refreshXtreamConnections() = run { loadXtreamConnections() }

fun ApplicationController.createXtreamConnection(name: String, url: String, username: String, password: String,
    scopes: XtreamScopes) = run {
    val remote = xtreamRemote()
    val owner = generation
    val created = remote.createXtreamConnection(name, url, username, password, scopes)
    publishXtreamDefaultLive(remote, owner)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = (next.xtreamConnections.orEmpty().filterNot { it.id == created.id } + created).sortedBy { it.id },
            xtreamNotice = "Connection added.")
    }
    profileConfigurationChanged()
}

fun ApplicationController.updateXtreamConnection(id: Long, name: String, scopes: XtreamScopes) = run {
    val remote = xtreamRemote()
    val owner = generation
    val updated = remote.updateXtreamConnection(id, name, scopes)
    publishXtreamDefaultLive(remote, owner)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.map { if (it.id == id) updated else it },
            xtreamNotice = "Connection saved.")
    }
    profileConfigurationChanged()
}

fun ApplicationController.replaceXtreamPassword(id: Long, password: String) = run {
    val remote = xtreamRemote()
    val owner = generation
    val updated = remote.replaceXtreamPassword(id, password)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.map { if (it.id == id) updated else it },
            xtreamNotice = "Password replaced.")
    }
    profileConfigurationChanged()
}

fun ApplicationController.syncXtreamConnection(id: Long) = mutate(MutationTarget(MutationKind.Xtream, xtreamTarget(id))) {
    val remote = xtreamRemote()
    val owner = generation
    val refresh = remote.syncXtreamConnection(id)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.map { if (it.id == id) it.copy(refresh = refresh) else it },
            xtreamNotice = "Sync requested. Refresh the list to check progress.")
    }
}

fun ApplicationController.setXtreamDefaultLive(id: Long) {
    val before = mutable.value
    mutate(MutationTarget(MutationKind.Xtream, xtreamTarget(id)),
        optimistic = { it.copy(xtreamDefaultLive = id) },
        revert = { it.copy(xtreamDefaultLive = before.xtreamDefaultLive) }) {
        val remote = xtreamRemote()
        val owner = generation
        val selected = remote.setXtreamDefaultLive(id)
        if (connection === remote && generation == owner) updateState { next ->
            next.copy(xtreamDefaultLive = selected, xtreamNotice = "Default live playlist saved.")
        }
        profileConfigurationChanged()
    }
}

fun ApplicationController.deleteXtreamConnection(id: Long) {
    val before = mutable.value
    mutate(MutationTarget(MutationKind.Xtream, xtreamTarget(id)),
        optimistic = { state -> state.copy(xtreamConnections = state.xtreamConnections?.filterNot { it.id == id },
            xtreamDefaultLive = state.xtreamDefaultLive.takeUnless { it == id }) },
        revert = { it.copy(xtreamConnections = before.xtreamConnections, xtreamDefaultLive = before.xtreamDefaultLive) }) {
        val remote = xtreamRemote()
        val owner = generation
        remote.deleteXtreamConnection(id)
        publishXtreamDefaultLive(remote, owner)
        if (connection === remote && generation == owner) updateState { next ->
            next.copy(xtreamConnections = next.xtreamConnections?.filterNot { it.id == id }, xtreamNotice = "Connection removed.")
        }
        profileConfigurationChanged()
    }
}

internal fun xtreamTarget(id: Long) = "Xtream connection $id"
