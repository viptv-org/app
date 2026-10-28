package org.viptv.app.updates

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** app#139: after Android replaced the app, the relaunch receiver reopens VIPTV with the new version. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateRelaunchReceiverTest {
    private val application = RuntimeEnvironment.getApplication()

    @Test fun theReplacedAppIsReopenedWithTheNewVersion() {
        UpdateRelaunchReceiver().onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        val launched = assertNotNull(shadowOf(application).nextStartedActivity, "VIPTV is not reopened after the update")
        assertEquals(UpdateRelaunchReceiver.MAIN_ACTIVITY, launched.component?.className)
        assertEquals(Intent.ACTION_MAIN, launched.action)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0, "Started from a broadcast receiver")
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0, "No stale page is restored")
        assertTrue(launched.hasExtra(UpdateRelaunchReceiver.EXTRA_UPDATED_TO))
        assertNotNull(launched.getStringExtra(UpdateRelaunchReceiver.EXTRA_UPDATED_TO), "MainActivity confirms this version")
    }

    @Test fun otherBroadcastsDoNothing() {
        for (action in listOf(Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_BOOT_COMPLETED, null)) {
            UpdateRelaunchReceiver().onReceive(application, Intent(action).setPackage(application.packageName))
            assertNull(shadowOf(application).nextStartedActivity, "$action must not reopen the app")
        }
    }
}