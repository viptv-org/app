package org.viptv.app.state

import org.viptv.app.connection.AccountAccess

private val ApplicationState.integrationParentBlocked: Boolean
    get() {
        val management = profileManagement ?: parentSettings
        val parent = management?.parent
        val kids = management?.profiles?.find { it.id == selectedProfile }?.kids == true
        return parent?.let { it.restricted && !it.unlocked } == true || (kids && parent?.unlocked != true)
    }

internal val ApplicationState.managesOwnIntegrations: Boolean
    get() = !integrationParentBlocked && AccountAccess(accountRole, manageAccount, manageIntegrations).managesSources

internal val ApplicationState.managesProfileIntegrations: Boolean
    get() {
        if (integrationParentBlocked || selectedProfile == null) return false
        val scope = browsingConfiguration?.profile?.scope ?: return false
        if (scope.profile_id != selectedProfile || accountIdentity?.id?.let { it != scope.account_id } == true) return false
        if (managesOwnIntegrations) return true
        val management = profileManagement ?: parentSettings ?: return false
        return management.profiles.any { it.id == selectedProfile }
    }
