package org.viptv.app.connection

internal actual fun deviceDisplayName(): String = android.os.Build.MODEL.orEmpty()
    .filterNot(Char::isISOControl).trim().take(24).let { model ->
        if (model.isEmpty()) "VIPTV for Android" else "VIPTV · $model"
    }
