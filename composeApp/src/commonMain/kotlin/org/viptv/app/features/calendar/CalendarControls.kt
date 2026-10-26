package org.viptv.app.features.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

/** A compact extension of the date rail; category and time remain separate choices. */
@Composable
internal fun CalendarControls(type: String, week: Int, television: Boolean, scale: Float,
    category: (String) -> Unit, shift: (Int) -> Unit, modifier: Modifier = Modifier,
    requester: FocusRequester? = null, down: FocusRequester? = null, range: String? = null) {
    if(!television){
        Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                listOf("series" to "TV","anime" to "Anime","movie" to "Movies").forEach{(id,label)->TextButton({category(id)}){Text(label,color=if(type==id)Palette.text else Palette.secondary)}}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                listOf(-1 to "Previous week",0 to "This week",1 to "Next week").forEach{(change,label)->TextButton({shift(change)},enabled=when(change){-1->week> -52;1->week<52;else->true}){Text(label)}}
            }
            range?.let{Text(it,color=Palette.secondary,fontSize=12.sp,modifier=Modifier.padding(horizontal=12.dp))}
        }
        return
    }
    org.viptv.app.components.TvFocusScroll(horizontal = true) { Row(modifier.then(if (television) Modifier else Modifier.horizontalScroll(rememberScrollState())),
        horizontalArrangement = Arrangement.spacedBy(if (television) 12.dp * scale else 4.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        val categories=listOf("series" to "TV", "anime" to "Anime", "movie" to "Movies")
        categories.forEach { (id,label) ->
            if (television) TvAction(Modifier.height(58.dp*scale).focusProperties { if(down!=null)this.down=down },scale,
                requester=requester.takeIf{type==id},activate={category(id)},
                background=if(type==id)Palette.raised else androidx.compose.ui.graphics.Color.Transparent,
                contentPadding=PaddingValues(horizontal=24.dp*scale)) { Text(label,color=if(type==id)it else Palette.secondary,fontSize=(24*scale).sp) }
            else TextButton({category(id)}) { Text(label,color=if(type==id)Palette.text else Palette.secondary) }
        }
        Spacer(Modifier.width(if(television)24.dp*scale else 8.dp))
        listOf(-1 to "Previous week",0 to "This week",1 to "Next week").forEach { (change,label)->
            if(television)TvAction(Modifier.height(58.dp*scale).focusProperties{if(down!=null)this.down=down},scale,
                activate={shift(change)},enabled=when(change){-1->week> -52;1->week<52;else->true},background=androidx.compose.ui.graphics.Color.Transparent,
                contentPadding=PaddingValues(horizontal=18.dp*scale)){Text(label,color=it,fontSize=(22*scale).sp)}
            else TextButton({shift(change)},enabled=when(change){-1->week> -52;1->week<52;else->true}){Text(label)}
        }
        range?.let{Text(it,Modifier.widthIn(max=500.dp*scale),color=Palette.secondary,fontSize=(18*scale).sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
    } }
}
