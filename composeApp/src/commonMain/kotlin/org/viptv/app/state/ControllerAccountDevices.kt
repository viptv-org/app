package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshAccountDevices() = run { loadAccountDevices() }

private suspend fun ApplicationController.loadAccountDevices() {
    val remote = requireNotNull(connection)
    val owner = generation
    val snapshot = accountDeviceRequest { it.accountDevices() }
    if (connection === remote && generation == owner)
        updateState { next -> next.copy(accountDevices = snapshot) }
}

fun ApplicationController.signOutDevice(id: String) = run {
    val device = mutable.value.accountDevices?.devices?.singleOrNull { it.id == id }
        ?: throw LocalFailure("Refresh the device list and try again.")
    if (device.current || !device.can_sign_out) throw LocalFailure("Use Sign out to leave this browser.")
    val remote = requireNotNull(connection)
    val owner = generation
    accountDeviceRequest { it.signOutDevice(id) }
    if (connection === remote && generation == owner)
        updateState { next -> next.copy(accountDevices = next.accountDevices?.let { devices ->
            devices.copy(devices = devices.devices.filterNot { it.id == id }) }) }
}

fun ApplicationController.signOutAllDevices() = run {
    if (mutable.value.accountDevices?.can_sign_out_all != true)
        throw LocalFailure("Refresh the device list and try again.")
    val remote = requireNotNull(connection)
    val owner = generation
    accountDeviceRequest { it.signOutAllDevices() }
    if (connection === remote && generation == owner) disconnect()
}

private suspend fun <T> ApplicationController.accountDeviceRequest(operation: suspend (RemoteConnection) -> T): T {
    val remote = requireNotNull(connection)
    try { return operation(remote) }
    catch (failure: RemoteFailure) {
        if (failure.status == 403 && connection === remote)
            updateState { next -> next.copy(accountDevices = null) }
        throw failure
    }
}
