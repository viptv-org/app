package org.viptv.experiment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.viptv.app.connection.AndroidAccountAuthorization

private const val PROOF_URL = "https://viptv-browser-fixture.invalid/proof?state=a%2Bb%2520c"

class BrowserLaunchQaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAccountAuthorization.attach(applicationContext)
        AndroidAccountAuthorization.open(PROOF_URL)
    }
}

class BrowserLaunchReceiverQaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(intent.action == Intent.ACTION_VIEW)
        check(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        check(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        check(intent.dataString == PROOF_URL)
        Log.i("VIPTV_BROWSER_QA", "PASS action_view=true browsable=true new_task=true encoded_query_preserved=true")
        finish()
    }
}
