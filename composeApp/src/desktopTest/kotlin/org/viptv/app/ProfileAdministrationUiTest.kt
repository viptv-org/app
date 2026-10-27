package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class ProfileAdministrationUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val parentLocked=Profile("other","Other",null,locked=true,lockKind="parent")
    @Test fun remoteParentNameAndAvatarEditsNeverAskForALocalPin(){
        var renamed:Pair<String,String>?=null
        val name=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){TvProfileEditDialog(TvProfileEditAction.Name,parentLocked,false,{}, {a,b->renamed=a to b},{_,_->},{},pinAvailable=false)}}
        try{frames(name);assertFalse(nodes(name).any{text(it)=="Current PIN"});val field=nodes(name).single{it.config.contains(SemanticsActions.SetText)};field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Renamed"));frames(name,20);click(name,"Save");assertEquals("Renamed" to "",renamed);capture(name,"remote-profile-name")}finally{name.close()}
        var avatar:Pair<AvatarPick,String>?=null
        runBlocking{loadAvatarCatalog()}
        val picker=ImageComposeScene(800,800,Density(1f)){ViptvTheme{AvatarPicker(parentLocked,false,AvatarPickerLayout.Desktop,{}, {a,b->avatar=a to b},pinAvailable=false)}}
        try{frames(picker);assertFalse(nodes(picker).any{text(it)=="Current PIN"});val choice=nodes(picker).first{it.config.contains(SemanticsActions.OnClick)&&it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Disney favorites 2")};assertFalse(choice.config.contains(SemanticsProperties.Disabled));choice.config[SemanticsActions.OnClick].action!!.invoke();assertEquals(AvatarPick("disney",2) to "",avatar)}finally{picker.close()}
    }
    @Test fun actualLocalPinRemainsRequiredAndRemotePolicyCannotBecomeLocalLock(){
        assertTrue(parentLocked.copy(lockKind=null).requiresLocalPin(true));assertFalse(parentLocked.requiresLocalPin(true));assertFalse(parentLocked.copy(lockKind=null).requiresLocalPin(false))
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){TvProfileEditDialog(TvProfileEditAction.Delete,parentLocked.copy(lockKind=null),false,{}, {_,_->},{_,_->},{},pinAvailable=true)}}
        try{frames(scene);assertTrue(nodes(scene).any{text(it)=="Current PIN"});assertTrue(clickable(scene,"Delete").config.contains(SemanticsProperties.Disabled))}finally{scene.close()}
    }
    @Test fun otherProfileLanguageGuidanceUsesSelectionAndNeverPromisesMenuLocalization(){
        var section:String?=null
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){TvProfileEditDialog(TvProfileEditAction.Language,parentLocked,false,{}, {_,_->},{_,_->},{},pinAvailable=false,editingCurrent=false,openSettings={section=it})}}
        try{frames(scene);assertTrue(nodes(scene).any{text(it).contains("Select Other in Profiles")});assertFalse(nodes(scene).any{text(it)=="Current PIN"});click(scene,"Choose profile");assertEquals("Subtitles & audio",section);capture(scene,"other-profile-language-guidance")}finally{scene.close()}
    }
    @Test fun existingTvSettingsHonorsAudioSectionEntry(){
        val scene=ImageComposeScene(1920,1080,Density(1f)){ViptvTheme(true){Surface(Modifier.fillMaxSize(),color=Palette.background){SettingsScreen(false,emptyList(),emptySet(),false,null,null,{}, {_,_->},{},{},{},initialSection="Subtitles & audio",television=true,tvPreferences=TvSettingsPreferences(),changeTvPreferences={})}}}
        try{frames(scene);assertTrue(nodes(scene).any{text(it).startsWith("Audio language")});assertTrue(nodes(scene).any{text(it).startsWith("Subtitle language")});capture(scene,"profile-audio-settings")}finally{scene.close()}
    }
    private fun frames(s:ImageComposeScene,offset:Int=0)=repeat(16){s.render((offset+it)*16_666_667L).close();Thread.sleep(3)}
    private fun nodes(s:ImageComposeScene)=s.semanticsOwners.flatMap{flatten(it.rootSemanticsNode)}
    private fun flatten(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::flatten)
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" "){it.text}
    private fun clickable(s:ImageComposeScene,label:String)=nodes(s).single{text(it)==label&&it.config.contains(SemanticsActions.OnClick)}
    private fun click(s:ImageComposeScene,label:String){assertTrue(clickable(s,label).config[SemanticsActions.OnClick].action!!.invoke())}
    private fun capture(scene:ImageComposeScene,name:String){System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let{path->File(path).mkdirs();scene.render(1_000_000_000L).use{image->image.encodeToData()!!.use{File(path,"$name.png").writeBytes(it.bytes)}}}}

}
