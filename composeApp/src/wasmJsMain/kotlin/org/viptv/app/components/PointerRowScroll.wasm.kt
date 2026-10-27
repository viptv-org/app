package org.viptv.app.components

/** Browsers report wheel steps in pixels or lines, never sized by the element. */
internal actual fun sidewaysWheelScale(width: Int, height: Int): Float = 1f
