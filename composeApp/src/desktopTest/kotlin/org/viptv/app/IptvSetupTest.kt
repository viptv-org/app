package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class IptvSetupTest {
    @Test fun credentialsWaitForProfileAndPinThenPersistOnlyToThatProfile() = runBlocking {
        val fixture = IptvSetupFixture(); val controller = fixture.controller()
        try {
            fixture.native.createProfile("Other")
            fixture.native.setProfilePin("default", "", "1234")
            val metadataOwner = fixture.native.browsingConfiguration("default")?.profile?.browsing_plugin
            val credentials = IptvCredentials(address = "https://provider.example", username = "private-user", password = "private-password")
            controller.connectIptv(fixture.path, credentials)
            assertEquals(Screen.Profiles, fixture.idle(controller).screen)
            assertNotNull(controller.state.value.profileNotice)
            assertTrue(fixture.native.sourceIntegrationSchemas("default").connections.isEmpty())
            controller.selectProfile("default")
            assertEquals(Screen.ProfilePin, controller.state.value.screen)
            controller.submitProfilePin("9999"); fixture.idle(controller)
            assertEquals(Screen.ProfilePin, controller.state.value.screen)
            assertTrue(fixture.native.sourceIntegrationSchemas("default").connections.isEmpty())
            controller.submitProfilePin("1234")
            val state = fixture.idle(controller)
            assertNull(state.error); assertEquals(Screen.Live, state.screen)
            assertEquals("default", state.selectedProfile); assertNull(controller.pendingIptv)
            assertEquals(1, fixture.liveReads)
            val schemas = fixture.native.sourceIntegrationSchemas("default")
            assertEquals("xtream", schemas.connections.single().plugin_id)
            val other = state.profiles.single { it.id != "default" }.id
            assertTrue(fixture.native.sourceIntegrationSchemas(other).connections.isEmpty())
            assertEquals(metadataOwner, fixture.native.browsingConfiguration("default")?.profile?.browsing_plugin)
            assertFalse(state.toString().contains("private-password"))
            assertFalse(credentials.toString().contains("private-user"))
            controller.disconnect()
            NativeLocalConnection(fixture.path, fixture.secrets).use { reopened ->
                assertEquals("xtream", reopened.sourceIntegrationSchemas("default").connections.single().plugin_id)
                assertTrue(reopened.sourceIntegrationSchemas(other).connections.isEmpty())
            }
            fixture.directory.toFile().walkTopDown().filter { it.isFile }.forEach { file ->
                assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("private-password"))
            }
        } finally { fixture.close(controller) }
    }

    @Test fun freshDefaultProfileAcceptsOnlyTheActiveM3uFields() = runBlocking {
        val fixture = IptvSetupFixture(); val controller = fixture.controller()
        try {
            controller.connectIptv(fixture.path, IptvCredentials(IptvMode.M3u,
                "https://provider.example/list.m3u?token=private", "unused-user", "unused-password"))
            val state = fixture.idle(controller)
            assertNull(state.error); assertEquals(Screen.Live, state.screen)
            assertTrue(state.localMode); assertNull(state.profileNotice)
            assertEquals("m3u", fixture.native.sourceIntegrationSchemas("default").connections.single().plugin_id)
            assertEquals(1, fixture.liveReads)
            assertFalse(state.toString().contains("token=private"))
        } finally { fixture.close(controller) }
    }
}
