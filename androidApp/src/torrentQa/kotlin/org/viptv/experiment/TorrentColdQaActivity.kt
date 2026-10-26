package org.viptv.experiment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.viptv.app.connection.AndroidTorrentRuntime
import org.viptv.app.qa.TorrentColdQaScreen

/** Included only by the explicitly opted-in, separate torrentqa package. */
class TorrentColdQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName.endsWith(".torrentqa"))
        AndroidTorrentRuntime.attach(applicationContext, TorrentRuntimeService::class.java)
        configurePlayerWindow(true, intent.getBooleanExtra("qa_tv", false))
        val specification = filesDir.resolve("torrent-qa-input.json").readText()
        setContent { TorrentColdQaScreen(specification, filesDir, ::finish) }
    }
}
