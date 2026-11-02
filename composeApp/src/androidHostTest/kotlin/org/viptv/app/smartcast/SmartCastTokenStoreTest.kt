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
    private val pin = "ab".repeat(32)

    private fun store(): Pair<SmartCastTokenStore, SharedPreferences> {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("test_vizio_tokens", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SmartCastTokenStore(preferences) { key } to preferences
    }

    @Test fun roundTripsAndClearsTokenAndPin() {
        val (store, _) = store()
        assertNull(store.loadToken(origin))
        assertNull(store.loadCertificatePin(origin))
        store.saveToken(origin, "bearer-token")
        store.saveCertificatePin(origin, pin)
        assertEquals("bearer-token", store.loadToken(origin))
        assertEquals(pin, store.loadCertificatePin(origin))
        store.clear(origin)
        assertNull(store.loadToken(origin))
        assertNull(store.loadCertificatePin(origin))
    }

    @Test fun neverWritesTokenOrPinInPlaintext() {
        val (store, preferences) = store()
        store.saveToken(origin, "bearer-token")
        store.saveCertificatePin(origin, pin)
        val stored = preferences.all.values.map { it as String }
        assertFalse(stored.any { it.contains("bearer-token") })
        assertFalse(stored.any { it.contains(pin) })
    }
}