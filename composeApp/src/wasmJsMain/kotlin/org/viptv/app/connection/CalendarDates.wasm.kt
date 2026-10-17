@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

import kotlinx.serialization.json.*
import org.viptv.app.features.calendar.CalendarDay

internal actual fun calendarWindow(offsetDays: Int): CalendarWindow {
    val value = Json.parseToJsonElement(calendarWindowJson(offsetDays)).jsonObject
    return CalendarWindow(value.getValue("start").jsonPrimitive.content, value.getValue("end").jsonPrimitive.content,
        value.getValue("days").jsonArray.map { raw -> val d = raw.jsonObject
            CalendarDay(d.getValue("id").jsonPrimitive.content, d.getValue("weekday").jsonPrimitive.content,
                d.getValue("number").jsonPrimitive.content, d.getValue("label").jsonPrimitive.content, d.getValue("fullDate").jsonPrimitive.content) })
}
internal actual fun calendarLabel(date: String): CalendarLabel? {
    val raw = calendarLabelJson(date).takeIf { it != "null" } ?: return null
    val value = Json.parseToJsonElement(raw).jsonObject
    return CalendarLabel(value.getValue("day").jsonPrimitive.content, value.getValue("time").jsonPrimitive.content, value.getValue("aired").jsonPrimitive.boolean)
}
@JsFun("""(offsetDays) => {
 const now = new Date(), id = d => d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
 const shifted = n => new Date(now.getFullYear(),now.getMonth(),now.getDate()+offsetDays+n);
 return JSON.stringify({start:id(shifted(-4)),end:id(shifted(4)),days:Array.from({length:7},(_,i) => {
  const n=i-3,d=shifted(n); return {id:id(d),weekday:d.toLocaleDateString(undefined,{weekday:'short'}),number:String(d.getDate()),label:id(d)===id(now)?'Today':d.toLocaleDateString(undefined,{weekday:'long'}),fullDate:d.toLocaleDateString(undefined,{year:'numeric',month:'short',day:'numeric'})};
 })});
}""")
private external fun calendarWindowJson(offsetDays: Int): String
@JsFun("""(value) => {
 const d=new Date(value); if(!Number.isFinite(d.getTime())) return 'null';
 const day=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
 return JSON.stringify({day,time:d.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'}),aired:d.getTime()<Date.now()});
}""")
private external fun calendarLabelJson(value: String): String
