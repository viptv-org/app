@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

import kotlinx.serialization.json.*
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime

internal actual fun calendarWindow(offsetDays: Int): CalendarWindow {
    val value = Json.parseToJsonElement(calendarWindowJson(offsetDays, APP_DATE_LOCALE)).jsonObject
    val range = calendarRequestRange(value.getValue("center").jsonPrimitive.content)
    return CalendarWindow(range.start, range.end,
        value.getValue("days").jsonArray.map { raw -> val d = raw.jsonObject
            CalendarDay(d.getValue("id").jsonPrimitive.content, d.getValue("weekday").jsonPrimitive.content,
                d.getValue("number").jsonPrimitive.content, d.getValue("label").jsonPrimitive.content, d.getValue("fullDate").jsonPrimitive.content) })
}
internal actual fun calendarLabel(date: String): CalendarLabel? {
    val raw = calendarLabelJson(date).takeIf { it != "null" } ?: return null
    val value = Json.parseToJsonElement(raw).jsonObject
    val time = value["hour"]?.jsonPrimitive?.intOrNull
        ?.let { hour -> formatTimeOfDay(hour, value.getValue("minute").jsonPrimitive.int, is24HourTime()) }
        ?: value.getValue("time").jsonPrimitive.content
    return CalendarLabel(value.getValue("day").jsonPrimitive.content, time, value.getValue("aired").jsonPrimitive.boolean)
}
@JsFun("""(offsetDays, locale) => {
 const now = new Date(), id = d => d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
 const shifted = n => new Date(now.getFullYear(),now.getMonth(),now.getDate()+offsetDays+n);
 return JSON.stringify({center:id(shifted(0)),days:Array.from({length:7},(_,i) => {
  const n=i-3,d=shifted(n); return {id:id(d),weekday:d.toLocaleDateString(locale,{weekday:'short'}),number:String(d.getDate()),label:id(d)===id(now)?'Today':d.toLocaleDateString(locale,{weekday:'long'}),fullDate:d.toLocaleDateString(locale,{year:'numeric',month:'short',day:'numeric'})};
 })});
}""")
private external fun calendarWindowJson(offsetDays: Int, locale: String): String
@JsFun("""(value) => {
 const plain = /^\d{4}-\d{2}-\d{2}$/.test(value);
 if (plain) {
  const parts = value.split('-').map(Number), d = new Date(parts[0], parts[1]-1, parts[2]);
  const id = x => x.getFullYear()+'-'+String(x.getMonth()+1).padStart(2,'0')+'-'+String(x.getDate()).padStart(2,'0');
  const now = new Date();
  return JSON.stringify({day:id(d), time:'All day', aired:id(d)<=id(now)});
 }
 const d=new Date(value); if(!Number.isFinite(d.getTime())) return 'null';
 const day=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
 return JSON.stringify({day, hour:d.getHours(), minute:d.getMinutes(), aired:d.getTime()<Date.now()});
}""")
private external fun calendarLabelJson(value: String): String
