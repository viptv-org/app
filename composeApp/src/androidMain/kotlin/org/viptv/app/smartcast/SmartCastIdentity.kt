package org.viptv.app.smartcast

import android.content.Context
import java.util.UUID

internal const val smartCastDeviceName = "VIPTV phone"

// Pairing and remote commands share one stable device ID, or a paired token would not match.
internal fun smartCastDeviceId(context: Context): String =
    context.getSharedPreferences("viptv_smartcast_pairing", Context.MODE_PRIVATE).let { prefs ->
        if (!prefs.contains("deviceId")) prefs.edit().putString("deviceId", UUID.randomUUID().toString()).apply()
        prefs.getString("deviceId", null) ?: UUID.randomUUID().toString()
    }