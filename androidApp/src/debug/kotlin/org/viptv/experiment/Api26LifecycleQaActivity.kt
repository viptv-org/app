package org.viptv.experiment

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi

@UnstableApi
class Api26LifecycleQaActivity : MainActivity() {
    var pauses = 0; private set
    var resumes = 0; private set
    var starts = 0; private set
    var stops = 0; private set
    override fun onPause() { pauses++; super.onPause() }
    override fun onResume() { super.onResume(); resumes++ }
    override fun onStart() { super.onStart(); starts++ }
    override fun onStop() { stops++; super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        check(android.os.Build.VERSION.SDK_INT == 26)
        check(getSystemService(android.app.UiModeManager::class.java).currentModeType == android.content.res.Configuration.UI_MODE_TYPE_NORMAL)
        super.onCreate(savedInstanceState)
        val origin = requireNotNull(intent.getStringExtra("qa_origin"))
        val parsed = android.net.Uri.parse(origin)
        require(parsed.scheme == "https" && parsed.host == "127.0.0.1" && parsed.port > 0)
        Log.i("VIPTV_API26_QA", "activity_created id=${System.identityHashCode(this)} restored=${savedInstanceState != null}")
        ViewModelProvider(this)[Api26LifecycleQaProbe::class.java].attach(this,
            ViewModelProvider(this)[ApplicationModel::class.java], origin, intent.getLongExtra("qa_sustain_ms", 0))
    }
}
