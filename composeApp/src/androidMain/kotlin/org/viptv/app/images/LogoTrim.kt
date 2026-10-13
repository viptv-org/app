package org.viptv.app.images

import android.graphics.Bitmap

internal actual fun trimLogoBitmap(input: Bitmap): Bitmap {
    if (input.width.toLong() * input.height > 2_000_000) return input
    val readable = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
    val pixels = IntArray(readable.width * readable.height)
    readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
    val bounds = logoBounds(readable.width, readable.height) { x, y -> pixels[y * readable.width + x] ushr 24 } ?: return input
    return Bitmap.createBitmap(readable, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
}
