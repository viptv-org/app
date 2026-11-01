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
}

fun ApplicationController.replaceXtreamPassword(id: Long, password: String) = run {
    val remote = xtreamRemote()
    val owner = generation
    val updated = remote.replaceXtreamPassword(id, password)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.map { if (it.id == id) updated else it },
            xtreamNotice = "Password replaced.")
    }
}

fun ApplicationController.syncXtreamConnection(id: Long) = run {
    val remote = xtreamRemote()
    val owner = generation
    val refresh = remote.syncXtreamConnection(id)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.map { if (it.id == id) it.copy(refresh = refresh) else it },
            xtreamNotice = "Sync requested. Refresh the list to check progress.")
    }
}

fun ApplicationController.setXtreamDefaultLive(id: Long) = run {
    val remote = xtreamRemote()
    val owner = generation
    val selected = remote.setXtreamDefaultLive(id)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamDefaultLive = selected, xtreamNotice = "Default live playlist saved.")
    }
}

fun ApplicationController.deleteXtreamConnection(id: Long) = run {
    val remote = xtreamRemote()
    val owner = generation
    remote.deleteXtreamConnection(id)
    publishXtreamDefaultLive(remote, owner)
    if (connection === remote && generation == owner) updateState { next ->
        next.copy(xtreamConnections = next.xtreamConnections?.filterNot { it.id == id }, xtreamNotice = "Connection removed.")
    }
}
