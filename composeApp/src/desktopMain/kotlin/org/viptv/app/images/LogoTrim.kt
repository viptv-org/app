package org.viptv.app.images

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect

internal actual fun trimLogoBitmap(input: Bitmap): Bitmap {
    val bounds = logoBounds(input.width, input.height) { x, y -> input.getColor(x, y) ushr 24 } ?: return input
    val result = Bitmap()
    return if (input.extractSubset(result, IRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom))) result else input
}
