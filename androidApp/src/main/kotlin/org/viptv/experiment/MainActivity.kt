package org.viptv.experiment

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.viptv.app.App
import org.viptv.app.connection.AndroidSecrets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        val tv = getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        setContent { App(television = tv, localDatabase = filesDir.resolve("local.sqlite").absolutePath) }
    }
}
