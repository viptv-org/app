package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class ProfileAdministrationTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun duplicateOrCompetingProfileMutationsDoNotCancelAnInFlightCreate()=runBlocking<Unit>{
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var writes=0;var bootstraps=0
        val client=HttpClient(MockEngine{request->
            val result=if(request.method==HttpMethod.Post){writes++;entered.complete(Unit);release.await();"{}"}else{bootstraps++;"{\"identity\":{\"profiles\":[{\"id\":\"one\",\"name\":\"One\"}]}}"}
            respond(result,headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }){configureTransport()}
        val remote=RemoteConnection("https://fixture.example",client=client,browserMode=true);val controller=ApplicationController(this, store)
        controller.connection=remote;controller.mutable.value=ApplicationState(screen=Screen.Profiles,profileToEdit="one",profiles=listOf(Profile("one","One",null)))
        try{controller.createProfile("New");entered.await();val original=controller.job
            controller.createProfile("New");controller.renameProfile("Other","");controller.setProfileAvatar("clay",2,"");controller.deleteProfile("");controller.setProfilePin("","1234");controller.selectProfile("one")
            assertSame(original,controller.job);assertTrue(original!!.isActive);assertEquals(1,writes)
            release.complete(Unit);original.join();assertEquals(1,writes);assertEquals(1,bootstraps);assertFalse(controller.state.value.busy)
        }finally{release.complete(Unit);controller.job?.cancelAndJoin();remote.close()}
    }
    @Test fun remotePinActionExplainsAccountPolicyWithoutAnyPerProfilePinRequest()=runBlocking<Unit>{
        var requests=0;val client=HttpClient(MockEngine{requests++;error("No per-profile PIN API")}){configureTransport()};val remote=RemoteConnection("https://fixture.example",client=client,browserMode=true);val c=ApplicationController(this, store);c.connection=remote;c.mutable.value=ApplicationState(screen=Screen.ProfileEdit,profileToEdit="one")
        try{c.setProfilePin("","1234");c.job?.join();assertEquals(0,requests);assertTrue(c.state.value.error.orEmpty().contains("account parent PIN"));assertFalse(c.state.value.error.orEmpty().contains("HTTPS"))}finally{remote.close()}
    }
    @Test fun existingSettingsEntryPreservesProfileScopeAndResetsOnOrdinaryNavigation()=runBlocking<Unit>{
        val client=HttpClient(MockEngine{error("Existing preferences need no transport")}){configureTransport()};val remote=RemoteConnection("https://fixture.example",client=client,browserMode=true);val c=ApplicationController(this, store);c.connection=remote
        c.mutable.value=ApplicationState(screen=Screen.ProfileEdit,selectedProfile="one",profileToEdit="two",managingProfiles=true,preferences=PreferencesSnapshot(preferences=ProfilePreferences(preferred_audio_language="fr")))
        try{c.profileSettings("Subtitles & audio");assertEquals(Screen.Profiles,c.state.value.screen);assertEquals("one",c.state.value.selectedProfile);assertFalse(c.state.value.managingProfiles);assertNull(c.state.value.settingsEntrySection)
            c.mutable.value=c.state.value.copy(screen=Screen.ProfileEdit,profileToEdit="one");c.profileSettings("Subtitles & audio");assertEquals(Screen.Settings,c.state.value.screen);assertEquals("Subtitles & audio",c.state.value.settingsEntrySection);assertEquals("fr",c.state.value.preferences?.preferences?.preferred_audio_language)
            c.navigate(Screen.Home);assertNull(c.state.value.settingsEntrySection);c.navigate(Screen.Settings);assertNull(c.state.value.settingsEntrySection)
            c.mutable.value=c.state.value.copy(screen=Screen.ProfileEdit,profileToEdit="two");c.profileSettings("Parental controls");assertEquals("one",c.state.value.selectedProfile);assertEquals("Parental controls",c.state.value.settingsEntrySection)
        }finally{remote.close()}
    }
}
