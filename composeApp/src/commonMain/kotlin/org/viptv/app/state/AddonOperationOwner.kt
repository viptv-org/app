package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.RemoteConnection

internal class AddonOperationOwner(
    val generation: Long,
    val device: LocalConnection?,
    val remote: RemoteConnection?,
    val profile: String?,
    private val coroutine: Job?,
) {
    suspend fun requireCurrent(controller: ApplicationController) {
        currentCoroutineContext().ensureActive()
        coroutine?.ensureActive()
        if (controller.generation != generation || controller.local !== device || controller.connection !== remote ||
            controller.mutable.value.selectedProfile != profile) throw CancellationException("Add-on operation was replaced.")
    }
}

internal suspend fun ApplicationController.addonOperationOwner() =
    AddonOperationOwner(generation, local, connection, mutable.value.selectedProfile, currentCoroutineContext()[Job])

internal suspend fun ApplicationController.markAddonOperation() { addonOperation = currentCoroutineContext()[Job] }

internal suspend fun ApplicationController.beginAddonOperation() = addonOperationOwner().also { markAddonOperation() }
