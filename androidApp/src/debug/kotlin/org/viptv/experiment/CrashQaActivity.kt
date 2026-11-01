package org.viptv.experiment

import android.app.Activity
import android.os.Bundle

class CrashQaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        throw RuntimeException("Forced crash for crash-report QA")
    }
}