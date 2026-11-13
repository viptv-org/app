package org.viptv.app

import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class IntegrationAccessTest {
    private fun state(role: String, account: Boolean = false, integrations: Boolean? = null): ApplicationState {
        val configuration = ProfileConfiguration(Scope("server", "account", "1", 1), ExecutionMode.SERVER,
            "Parent", browsing_plugin = "stremio", assignments = emptyList())
        return ApplicationState(selectedProfile = "1", accountRole = role, manageAccount = account,
            manageIntegrations = integrations, profileManagement = parentFixture(),
            browsingConfiguration = BrowsingConfiguration(configuration, emptyList()))
    }

    @Test fun ownIntegrationAuthorityIsSeparateFromGlobalAdministrationAndLegacyFallbackIsOwnerOnly() {
        assertTrue(state("member", integrations = true).managesOwnIntegrations)
        assertFalse(state("member", account = true).managesOwnIntegrations)
        assertFalse(state("member", integrations = false).managesOwnIntegrations)
        assertTrue(state("owner", account = true).managesOwnIntegrations)
        assertFalse(state("owner", account = true, integrations = false).managesOwnIntegrations)
        assertFalse(state("device", account = true, integrations = true).managesOwnIntegrations)
    }

    @Test fun profileActivationRetainsItsOwnScopedAuthorityWithoutAccountManagement() {
        for (role in listOf("owner", "member", "device")) {
            val current = state(role, integrations = false)
            assertFalse(current.managesOwnIntegrations)
            assertTrue(current.managesProfileIntegrations)
            assertFalse(current.copy(selectedProfile = "2").managesProfileIntegrations)
            assertFalse(current.copy(accountIdentity = AccountIdentity("other", "other", "Other", "member")).managesProfileIntegrations)
        }
    }

    @Test fun explicitEffectiveServerAuthorityWorksWithoutAnOptionalManagementProjectionButKnownLocksWin() {
        val member = state("member", integrations = true)
        assertTrue(member.copy(profileManagement = null).managesOwnIntegrations)
        assertTrue(member.copy(profileManagement = null).managesProfileIntegrations)
        val locked = parentFixture(kids = true, unlocked = false, restricted = true)
        val child = member.copy(selectedProfile = "2", profileManagement = locked)
        assertFalse(child.managesOwnIntegrations)
        assertFalse(child.managesProfileIntegrations)
        assertFalse(member.copy(profileManagement = locked).managesOwnIntegrations)
        assertFalse(member.copy(manageIntegrations = false, profileManagement = null).managesProfileIntegrations)
    }
}
