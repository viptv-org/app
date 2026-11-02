package org.viptv.app.features.settings

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchOnTvAvailableTest {
    @Test fun phoneAndTabletOfferWatchOnTv() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.uiMode = Configuration.UI_MODE_TYPE_NORMAL
        assertTrue(watchOnTvAvailable(context))
    }

    @Test fun televisionNeverOffersWatchOnTv() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.uiMode = Configuration.UI_MODE_TYPE_TELEVISION
        assertFalse(watchOnTvAvailable(context))
    }
}