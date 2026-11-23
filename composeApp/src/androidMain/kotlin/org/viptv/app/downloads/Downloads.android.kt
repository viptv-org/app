package org.viptv.app.downloads

internal actual fun platformDownloads(): Downloads? = AndroidDownloads.engine
