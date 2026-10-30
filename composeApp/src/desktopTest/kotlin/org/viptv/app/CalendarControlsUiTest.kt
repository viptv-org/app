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
        try{frames(scene);open(scene,"Type: All");pick(scene,"Anime");assertEquals("anime",category);click(scene,"Next week");assertEquals(1,shifted);val action=clickable(scene,"Find S2 E4");assertTrue(action.boundsInRoot.left>=1240);action.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(1,watched);assertEquals(0,opened);click(scene,"Tomorrow");await(scene,20,"Tomorrow's empty day"){nodes(scene).any{text(it)=="No releases for this day."}};click(scene,"Today");await(scene,40,"Today's release"){nodes(scene).any{text(it)=="Find S2 E4"}};capture(scene,"tv-calendar-controls")}finally{scene.close()}
    }
    @Test fun phoneAndDesktopPreserveEmptyDatesAndOfferExplicitEpisodeAction(){
        for(width in listOf(390,1200)){
            var watched=0
            val scene=ImageComposeScene(width,900,Density(1f)){ViptvTheme(false){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry),false,{},{},controls=true,watch={watched++})}}}
            try{frames(scene);assertTrue(nodes(scene).any{text(it).contains("Tomorrow")||text(it).contains("Fri")});click(scene,"Find S2 E4");assertEquals(1,watched);capture(scene,if(width==390)"phone-calendar-controls" else "desktop-calendar-controls");
                if(width==390){val emptyDate=nodes(scene).single{text(it).contains("Fri")&&it.config.contains(SemanticsActions.OnClick)};emptyDate.config[SemanticsActions.OnClick].action!!.invoke()}else click(scene,"Tomorrow");await(scene,150,"Tomorrow's empty day"){nodes(scene).any{text(it)=="No releases for this day."}};capture(scene,if(width==390)"phone-calendar-empty-day" else "desktop-calendar-empty-day")}finally{scene.close()}
        }
    }
    @Test fun tvFocusReturnsToSelectedReleaseWhileLaterPagesArrive(){
        val entries=mutableStateOf(listOf(entry));var selected=""
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){Surface(Modifier.fillMaxSize(),color=Palette.background){TvCalendarScreen(days,entries.value,{}, {},controls=true,categoryChanged={selected=it},watchRelease={})}}}
        try{frames(scene);clickable(scene,"Today").config[SemanticsActions.RequestFocus].action!!.invoke();frames(scene,20);val row=nodes(scene).single{ text(it).contains("Scheduled series")&&it.config.contains(SemanticsActions.RequestFocus)&&it.config.contains(SemanticsActions.OnClick)};assertTrue(row.config[SemanticsActions.RequestFocus].action!!.invoke());frames(scene,40);entries.value=listOf(entry,entry.copy(id="second",card=card.copy(title="Later row")));frames(scene,60);assertTrue(nodes(scene).any{text(it).contains("Scheduled series")&&it.config.getOrNull(SemanticsProperties.Focused)==true});assertEquals("",selected)}finally{scene.close()}
    }
    @Test fun calendarDefaultsToEveryTypeAndYourShowsAndTheChipsSwitchBothWaysAndClear(){
        val library=entry.copy(id="library",card=card.copy(identity=MediaIdentity("simkl:tv:21","series",emptyList()),title="Library series"),source=CalendarSource.Library)
        for(tv in listOf(false,true)){
            val layout=if(tv)"TV" else "desktop"
            var category=""
            val scene=ImageComposeScene(if(tv)1920 else 1200,900,Density(1f)){ViptvTheme(tv){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry,library),tv,{},{},controls=true,categoryChanged={category=it})}}}
            try{
                frames(scene)
                assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)=="Type: All"},"$layout: every type is the default")
                assertTrue(nodes(scene).any{text(it).contains("Library series")},"$layout: your shows are shown")
                assertFalse(nodes(scene).any{text(it).contains("Scheduled series")},"$layout: your shows come first; texts="+nodes(scene).map(::text).filter(String::isNotBlank))
                assertFalse(nodes(scene).any{text(it)=="Clear"},"$layout: nothing to clear yet")
                assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)=="Shows: Your shows"},"$layout: the Show chip states the default")
                open(scene,"Shows: Your shows");pick(scene,"All")
                assertTrue(nodes(scene).any{text(it).contains("Scheduled series")},"$layout: All adds the public release; texts="+nodes(scene).map(::text).filter(String::isNotBlank))
                open(scene,"Shows: All");pick(scene,"Your shows")
                assertFalse(nodes(scene).any{text(it).contains("Scheduled series")},"$layout: Your shows hides it again")
                open(scene,"Type: All");pick(scene,"Movies");assertEquals("movie",category,"$layout: the type chip applies at once")
                click(scene,"Clear");frames(scene,10)
                assertEquals("all",category,"$layout: Clear returns to every type")
                assertTrue(nodes(scene).any{text(it).contains("Library series")},"$layout: Clear returns to your shows")
                assertFalse(nodes(scene).any{text(it).contains("Scheduled series")},"$layout: and not to all releases")
                assertFalse(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.Button&&text(it)=="Clear"})
            }finally{scene.close()}
        }
    }
    @Test fun anEmptyWeekFromYourListShowsEveryReleaseWithALine(){
        for(tv in listOf(false,true)){
            val layout=if(tv)"TV" else "desktop"
            val scene=ImageComposeScene(if(tv)1920 else 1200,900,Density(1f)){ViptvTheme(tv){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry),tv,{},{},controls=true,sources=setOf(CalendarSource.Library,CalendarSource.Public))}}}
            try{
                frames(scene)
                assertTrue(nodes(scene).any{text(it)==CALENDAR_FALLBACK},"$layout: the fallback says why; texts="+nodes(scene).map(::text).filter(String::isNotBlank))
                assertTrue(nodes(scene).any{text(it).contains("Scheduled series")},"$layout: the public releases are shown")
                assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)=="Shows: Your shows"},"$layout: the Show chip keeps the picked source")
                open(scene,"Shows: Your shows");pick(scene,"All")
                assertFalse(nodes(scene).any{text(it)==CALENDAR_FALLBACK},"$layout: picking All drops the line")
            }finally{scene.close()}
        }
    }
    @Test fun theRemoteReachesTheAllTypeAndOpensOnIt(){
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(days,listOf(entry),true,{},{},controls=true)}}}
        try{
            frames(scene)
            val chip=nodes(scene).single{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)=="Type: All"}
            assertTrue(chip.config.contains(SemanticsActions.RequestFocus),"the remote reaches the type chip")
            open(scene,"Type: All");frames(scene,40)
            assertTrue(nodes(scene).any{it.config.getOrNull(SemanticsProperties.Focused)==true&&text(it).startsWith("All")},
                "the panel opens with the remote on All; focused="+nodes(scene).filter{it.config.getOrNull(SemanticsProperties.Focused)==true}.map(::text))
            assertTrue(nodes(scene).any{text(it).contains("All")&&text(it).contains("Current")},"All is the current type")
        }finally{scene.close()}
    }
    @Test fun phoneKeepsTheWeekAndTheFiltersOnSeparateRowsThatScrollOnTheirOwn(){
        val library=entry.copy(id="library",card=card.copy(identity=MediaIdentity("simkl:tv:21","series",emptyList()),title="Library series"),source=CalendarSource.Library)
        val weekDays=listOf(CalendarDay("one","Thu","15","Today","Thursday, October 15"),CalendarDay("two","Fri","16","Tomorrow","Friday, October 16"))
        val scene=ImageComposeScene(390,900,Density(1f)){ViptvTheme(false){Surface(Modifier.fillMaxSize(),color=Palette.background){CalendarScreen(weekDays,listOf(entry,library),false,{},{},controls=true)}}}
        try{
            frames(scene)
            val week=weekRow(scene);val filters=filterRow(scene)
            assertTrue(week.maxOf{it.boundsInRoot.bottom}<=filters.minOf{it.boundsInRoot.top},
                "the week chips sit above the filter chips; week="+week.map{it.boundsInRoot}+" filters="+filters.map{it.boundsInRoot})
            val bands=listOf(week,filters).map{row->row.minOf{it.boundsInRoot.top} to row.maxOf{it.boundsInRoot.bottom}}
            var strips=bands.map{strip(scene,it)}
            listOf(week,filters).forEachIndexed{index,row->
                val rowStrip=strips[index]
                near(390f,rowStrip.boundsInRoot.width,"row $index spans the screen")
                row.forEach{control->
                    assertTrue(control.boundsInRoot.top>=rowStrip.boundsInRoot.top&&control.boundsInRoot.bottom<=rowStrip.boundsInRoot.bottom,"row $index holds its own controls")
                    val onScreen=control.boundsInRoot.left>=rowStrip.boundsInRoot.left&&control.boundsInRoot.right<=rowStrip.boundsInRoot.right
                    assertTrue(onScreen||reach(rowStrip).second>0f,"row $index shows its controls or scrolls to them")
                }
            }
            val overflowing=strips.indices.filter{reach(strips[it]).second>0f}
            assertTrue(overflowing.isNotEmpty(),"the week's navigation overflows the 390 dp row")
            for(index in overflowing){
                val before=strips.map(::reach)
                val (value,end)=before[index]
                assertTrue(strips[index].config[SemanticsActions.ScrollBy].action!!.invoke(end-value,0f),"row $index scrolls by its own overflow")
                frames(scene,10)
                strips=listOf(bands[0],bands[1]).map{strip(scene,it)}
                val after=strips.map(::reach)
                near(end,after[index].first,"row $index reaches its end")
                strips.indices.filter{it!=index}.forEach{other->near(before[other].first,after[other].first,"row $other stays put")}
            }
            val range=nodes(scene).firstOrNull{text(it)=="Thursday, October 15 – Friday, October 16"}?:fail("the date range is not on the week row")
            assertTrue(range.boundsInRoot.top>=bands[0].first&&range.boundsInRoot.bottom<=bands[0].second,"the date range rides the week row")
        }finally{scene.close()}
    }
    private fun weekRow(s:ImageComposeScene)=listOf("Previous week","This week","Next week").map{label->nodes(s).single{text(it)==label&&it.config.contains(SemanticsActions.OnClick)}}
    private fun filterRow(s:ImageComposeScene)=listOf("Type: All","Shows: Your shows").map{label->nodes(s).single{it.config.getOrNull(SemanticsProperties.Role)==Role.DropdownList&&text(it)==label}}
    private fun strip(s:ImageComposeScene,band:Pair<Float,Float>)=nodes(s).filter{it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)}
        .single{it.boundsInRoot.top<=band.first&&it.boundsInRoot.bottom>=band.second}
    private fun reach(node:SemanticsNode)=node.config[SemanticsProperties.HorizontalScrollAxisRange].let{range->range.value() to range.maxValue()}
    private fun near(expected:Float,actual:Float,what:String)=assertTrue(kotlin.math.abs(expected-actual)<=1f,"$what: expected $expected, was $actual")
    private fun frames(s:ImageComposeScene,offset:Int=0)=repeat(20){s.render((offset+it)*16_666_667L).close();Thread.sleep(3)}
    private fun await(s:ImageComposeScene,offset:Int,what:String,condition:()->Boolean){
        val deadline=System.nanoTime()+2_000_000_000L
        var frame=offset
        do{s.render(frame++*16_666_667L).close();if(condition())return;Thread.sleep(3)}while(System.nanoTime()<deadline)
        fail("$what did not appear; texts="+nodes(s).map(::text).filter{it.isNotBlank()})
    }
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
