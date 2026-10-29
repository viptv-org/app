package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.calendar.*
import org.viptv.app.theme.*
import org.viptv.app.preview.PreviewTvCalendar
import org.viptv.engine.wire.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class CalendarControlsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val days=listOf(CalendarDay("one","Thu","15","Today","Oct15"),CalendarDay("two","Fri","16","Tomorrow","Oct16"))
    private val card=PreviewTvCalendar.entries.first().card.copy(identity=MediaIdentity("simkl:tv:20","series",emptyList()),provider_id="simkl",title="Scheduled series",description="An actual scheduled release.",metadata_origin=MetadataOrigin("simkl"))
    private val entry=CalendarEntry("release","one","4 PM",card,subtitle="S2 E4 · Actual episode",aired=true,description="An actual scheduled release.",season=2,episode=4)
    @Test fun tvControlsAndWatchActionRemainWithinTheExistingRailsAndKeepEmptyDateSelectable(){
        var category="series";var shifted=0;var watched=0;var opened=0
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry),true,{opened++},{},controls=true,categoryChanged={category=it},weekChanged={shifted=it},watch={watched++})}}}
        try{frames(scene);open(scene,"Type: TV");pick(scene,"Anime");assertEquals("anime",category);click(scene,"Next week");assertEquals(1,shifted);val action=clickable(scene,"Find S2 E4");assertTrue(action.boundsInRoot.left>=1240);action.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,watched);assertEquals(0,opened);click(scene,"Tomorrow");frames(scene,20);assertTrue(nodes(scene).any{text(it)=="No releases for this day."});click(scene,"Today");frames(scene,40);assertTrue(nodes(scene).any{text(it)=="Find S2 E4"});capture(scene,"tv-calendar-controls")}finally{scene.close()}
    }
    @Test fun phoneAndDesktopPreserveEmptyDatesAndOfferExplicitEpisodeAction(){
        for(width in listOf(390,1200)){
            var watched=0
            val scene=ImageComposeScene(width,900,Density(1f)){ViptvTheme(false){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry),false,{},{},controls=true,watch={watched++})}}}
            try{frames(scene);assertTrue(nodes(scene).any{text(it).contains("Tomorrow")||text(it).contains("Fri")});click(scene,"Find S2 E4");assertEquals(1,watched);capture(scene,if(width==390)"phone-calendar-controls" else "desktop-calendar-controls");
                if(width==390){val emptyDate=nodes(scene).single{text(it).contains("Fri")&&it.config.contains(SemanticsActions.OnClick)};emptyDate.config[SemanticsActions.OnClick].action!!.invoke()}else click(scene,"Tomorrow");frames(scene,150);assertTrue(nodes(scene).any{text(it)=="No releases for this day."});capture(scene,if(width==390)"phone-calendar-empty-day" else "desktop-calendar-empty-day")}finally{scene.close()}
        }
    }
    @Test fun tvFocusReturnsToSelectedReleaseWhileLaterPagesArrive(){
        val entries=mutableStateOf(listOf(entry));var selected=""
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){Surface(Modifier.fillMaxSize(),color=Palette.background){TvCalendarScreen(days,entries.value,{}, {},controls=true,categoryChanged={selected=it},watchRelease={})}}}
        try{frames(scene);clickable(scene,"Today").config[SemanticsActions.RequestFocus].action!!.invoke();frames(scene,20);val row=nodes(scene).single{ text(it).contains("Scheduled series")&&it.config.contains(SemanticsActions.RequestFocus)&&it.config.contains(SemanticsActions.OnClick)};assertTrue(row.config[SemanticsActions.RequestFocus].action!!.invoke());frames(scene,40);entries.value=listOf(entry,entry.copy(id="second",card=card.copy(title="Later row")));frames(scene,60);assertTrue(nodes(scene).any{text(it).contains("Scheduled series")&&it.config.getOrNull(SemanticsProperties.Focused)==true});assertEquals("",selected)}finally{scene.close()}
    }
    @Test fun calendarFiltersAreChipsThatApplyAtOnceAndClear(){
        val library=entry.copy(id="library",card=card.copy(identity=MediaIdentity("simkl:tv:21","series",emptyList()),title="Library series"),source=CalendarSource.Library)
        for(tv in listOf(false,true)){
            val scene=ImageComposeScene(if(tv)1920 else 1200,900,Density(1f)){ViptvTheme(tv){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry,library),tv,{},{},controls=true)}}}
            try{
                frames(scene)
                assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)=="Type: TV"},if(tv)"TV: the type is a chip" else "desktop: the type is a chip")
                assertFalse(nodes(scene).any{text(it)=="Clear"},"nothing to clear yet")
                open(scene,"Shows: All")
                pick(scene,"Your shows")
                assertTrue(nodes(scene).any{text(it)=="Library series"})
                assertFalse(nodes(scene).any{text(it)=="Scheduled series"},"the source filter applies at once")
                click(scene,"Clear")
                frames(scene,10)
                assertTrue(nodes(scene).any{text(it)=="Scheduled series"},"Clear drops the source filter")
                assertFalse(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.Button&&text(it)=="Clear"})
            }finally{scene.close()}
        }
    }
    private fun frames(s:ImageComposeScene,offset:Int=0)=repeat(20){s.render((offset+it)*16_666_667L).close();Thread.sleep(3)}
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{flatten(it.rootSemanticsNode)}
    private fun flatten(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::flatten)
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" "){it.text}
    private fun clickable(s:ImageComposeScene,label:String)=nodes(s).single{text(it)==label&&it.config.contains(SemanticsActions.OnClick)}
    private fun click(s:ImageComposeScene,label:String){assertTrue(clickable(s,label).config[SemanticsActions.OnClick].action!!.invoke())}
    private fun open(s:ImageComposeScene,label:String){
        val trigger=nodes(s).single{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)==label}
        assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke());frames(s)
    }
    private fun pick(s:ImageComposeScene,label:String){
        val row=nodes(s).single{it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton,Role.Checkbox)&&text(it).startsWith(label)}
        assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke());frames(s)
    }
    private fun capture(s:ImageComposeScene,name:String){System.getenv("VIPTV_CALENDAR_CAPTURE_DIR")?.let{path->File(path).mkdirs();s.render(2_000_000_000L).use{image->image.encodeToData()!!.use{File(path,"$name.png").writeBytes(it.bytes)}}}}
}
