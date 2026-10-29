package org.viptv.experiment

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

class PauseOnlyQaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Disposable playback lifecycle overlay"; setPadding(24, 24, 24, 24) })
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
