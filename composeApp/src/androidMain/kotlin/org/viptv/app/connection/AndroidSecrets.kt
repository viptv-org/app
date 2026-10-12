package org.viptv.app.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import uniffi.viptv_core.SecretStore

private const val KEY_ALIAS = "viptv-experiment-addons"

object AndroidSecrets {
    private lateinit var context: Context
    fun attach(context: Context) { this.context = context.applicationContext }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun vault(): SecretStore = object : SecretStore {
        private val preferences = context.getSharedPreferences("addon-secrets", Context.MODE_PRIVATE)
        override fun get(key: String): String? = preferences.getString(key, null)?.let { sealed ->
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            require(bytes.size >= 13)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
            }
        }
        override fun put(key: String, value: String) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val sealed = cipher.iv + cipher.doFinal(value.encodeToByteArray())
            check(preferences.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
        }
        override fun remove(key: String) { check(preferences.edit().remove(key).commit()) }
    }
}

actual fun platformSecrets(): SecretStore = AndroidSecrets.vault()
