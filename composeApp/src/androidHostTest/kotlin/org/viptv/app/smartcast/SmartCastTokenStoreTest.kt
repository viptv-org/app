package org.viptv.app.smartcast

import android.content.Context
import android.content.SharedPreferences
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartCastTokenStoreTest {
    private val origin = "https://192.0.2.10:7345"

    private fun store(): Pair<SmartCastTokenStore, SharedPreferences> {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("test_vizio_tokens", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SmartCastTokenStore(preferences) { key } to preferences
    }

    @Test fun roundTripsAndClearsAToken() {
        val (store, _) = store()
        assertNull(store.load(origin))
        store.save(origin, "bearer-token")
        assertEquals("bearer-token", store.load(origin))
        store.clear(origin)
        assertNull(store.load(origin))
    }

    @Test fun neverWritesTheTokenInPlaintext() {
        val (store, preferences) = store()
        store.save(origin, "bearer-token")
        val sealed = preferences.all.values.single() as String
        assertFalse(sealed.contains("bearer-token"))
    }
}