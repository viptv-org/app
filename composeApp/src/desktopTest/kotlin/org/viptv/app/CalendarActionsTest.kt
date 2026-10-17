package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.calendar.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class CalendarActionsTest {
    private val codec=Json { encodeDefaults=true }
    private val origin=MetadataOrigin("simkl")
    private val parent=Card(MediaIdentity("simkl:tv:20","series",emptyList()),provider_id="simkl",title="Scheduled series",badges=emptyList(),metadata_origin=origin)
    private fun episode(id:String, season:Long=2, number:Long=4, order:String="aired", source:MetadataOrigin=origin)=parent.copy(identity=MediaIdentity(id,"series",emptyList(),EpisodeCoordinates(order,season,number)),parent_identity=parent.identity,title="Actual episode",metadata_origin=source)
    private fun entry()=CalendarEntry("release","2026-10-15","4 PM",parent,aired=true,season=2,episode=4,releaseDate="2026-10-15T16:00:00Z")
    private fun controller(client:HttpClient,scope:CoroutineScope):Pair<ApplicationController,RemoteConnection>{
        val remote=RemoteConnection("https://fixture.example",client=client,browserMode=false)
        return ApplicationController(scope).also { it.connection=remote;it.mutable.value=ApplicationState(screen=Screen.Calendar,selectedProfile="one",preferences=PreferencesSnapshot()) } to remote
    }
    @Test fun resolvesOnlyActualScheduledCardUsingSeasonOffsetBeforeStartingSources()=runBlocking<Unit>{
        val target=episode("actual-provider-id");val offsets=mutableListOf<String?>();var requested:String?=null
        val client=HttpClient(MockEngine{request->
            val response=when{
                request.url.encodedPath.startsWith("/api/meta/")->{offsets+=request.url.parameters["offset"];codec.encodeToString(if(offsets.size==1)DetailSnapshot(parent,episodes=listOf(episode("earlier",1,1)),next_cursor="17",seasons=listOf(EpisodeSeason(2,2,55)))else DetailSnapshot(parent,episodes=listOf(target),next_cursor=null))}
                request.url.encodedPath=="/api/streams"->{val body=Json.parseToJsonElement((request.body as TextContent).text).jsonObject;requested=body["id"]?.jsonPrimitive?.content;"{\"id\":\"job\"}"}
                request.url.encodedPath=="/api/streams/job"->"{\"done\":true,\"events\":[]}"
                request.url.encodedPath=="/api/profiles/one/source-ranking"->"{\"streams\":[]}"
                else->error("Unexpected calendar action ${request.url}")
            };respond(response,headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }){configureTransport()}
        val(c,r)=controller(client,this)
        try{c.watchCalendar(entry());withTimeout(3000){while(requested==null)delay(10)};c.job?.join();assertEquals(listOf<String?>("0","55"),offsets);assertEquals("actual-provider-id",requested);assertEquals(target,c.state.value.sourceTitle);assertEquals(Screen.Sources,c.state.value.screen)}finally{c.job?.cancelAndJoin();r.close()}
    }
    @Test fun ambiguousCoordinatesAndUnmappedAnimeStayInDetailsWithoutSources()=runBlocking<Unit>{
        for(cards in listOf(listOf(episode("one"),episode("two")),listOf(episode("absolute",order="anidb")))){
            var calls=0
            val client=HttpClient(MockEngine{calls++;assertTrue(it.url.encodedPath.startsWith("/api/meta/"));respond(codec.encodeToString(DetailSnapshot(parent,episodes=cards)),headers=headersOf(HttpHeaders.ContentType,"application/json"))}){configureTransport()}
            val(c,r)=controller(client,this)
            try{c.watchCalendar(entry());c.job?.join();assertEquals(Screen.Details,c.state.value.screen);assertNotNull(c.state.value.detail);assertTrue(c.state.value.error.orEmpty().contains("matched safely"));assertEquals(1,calls)}finally{r.close()}
        }
    }
    @Test fun categoryAndWeekRequestsRetainCoordinatesAnd422IsUnavailable()=runBlocking<Unit>{
        val requests=mutableListOf<String>()
        val client=HttpClient(MockEngine{request->requests+=request.url.toString();if(request.url.parameters["type"]=="movie")respond("{}",HttpStatusCode.UnprocessableEntity,headersOf(HttpHeaders.ContentType,"application/json"))else respond("{\"entries\":[],\"complete\":true}",headers=headersOf(HttpHeaders.ContentType,"application/json"))}){configureTransport()}
        val(c,r)=controller(client,this)
        try{c.calendarCategory("anime");c.job?.join();assertTrue(requests.last().contains("type=anime"));val first=requests.last();c.calendarWeek(1);c.job?.join();assertEquals(1,c.state.value.calendarWeek);assertNotEquals(first,requests.last());c.calendarCategory("movie");c.job?.join();assertFalse(c.state.value.calendarAvailable);assertNull(c.state.value.error);assertTrue(c.state.value.calendarEntries.isEmpty());assertEquals(3,requests.size)}finally{r.close()}
        val page=CalendarPage(listOf(CalendarRelease("release",parent,"2026-10-15T16:00:00Z",season=2,episode=4)),complete=true)
        val client2=HttpClient(MockEngine{respond(codec.encodeToString(page),headers=headersOf(HttpHeaders.ContentType,"application/json"))}){configureTransport()};val remote=RemoteConnection("https://fixture.example",client=client2,browserMode=false)
        try{val day=requireNotNull(calendarLabel("2026-10-15T16:00:00Z")).day;val mapped=remote.calendar(CalendarWindow(day,day,listOf(CalendarDay(day,"Thu","15","Today","Oct15")))).single();assertEquals(2L,mapped.season);assertEquals(4L,mapped.episode);assertEquals("2026-10-15T16:00:00Z",mapped.releaseDate)}finally{remote.close()}
    }
    @Test fun aChangedOriginOrNavigationNeverStartsScheduledPlayback()=runBlocking<Unit>{
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val client=HttpClient(MockEngine{entered.complete(Unit);withContext(NonCancellable){gate.await()};respond(codec.encodeToString(DetailSnapshot(parent,episodes=listOf(episode("actual")))),headers=headersOf(HttpHeaders.ContentType,"application/json"))}){configureTransport()};val(c,r)=controller(client,this)
        try{c.watchCalendar(entry());entered.await();c.navigate(Screen.Home);gate.complete(Unit);c.job?.join();assertEquals(Screen.Home,c.state.value.screen);assertNull(c.state.value.sourceTitle)}finally{gate.complete(Unit);r.close()}
        assertTrue(calendarMatches(listOf(episode("other",source=MetadataOrigin("stremio","addon:1","a".repeat(32)))),parent,2,4).isEmpty())
    }
    @Test fun missingScheduledEpisodeStopsAfterThreeTargetedPages()=runBlocking<Unit>{
        var calls=0
        val client=HttpClient(MockEngine{request->
            assertTrue(request.url.encodedPath.startsWith("/api/meta/"));calls++
            val offset=request.url.parameters["offset"]?.toIntOrNull()?:0
            val page=DetailSnapshot(parent,episodes=listOf(episode("page$calls",number=100L+calls)),next_cursor=(offset+128).toString(),seasons=listOf(EpisodeSeason(2,1000,55)))
            respond(codec.encodeToString(page),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }){configureTransport()};val(c,r)=controller(client,this)
        try{c.watchCalendar(entry());c.job?.join();assertEquals(4,calls);assertEquals(Screen.Details,c.state.value.screen);assertNull(c.state.value.sourceTitle);assertTrue(c.state.value.error.orEmpty().contains("matched safely"))}finally{r.close()}
    }

}
