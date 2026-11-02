package org.viptv.app.smartcast

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SmartCastCredentialStore {
    fun load(tvOrigin: String): String?
    fun save(tvOrigin: String, token: String)
    fun clear(tvOrigin: String)
}

class SmartCastTokenStore internal constructor(
    private val preferences: SharedPreferences,
    private val keyProvider: () -> SecretKey,
) : SmartCastCredentialStore {
    constructor(context: Context) : this(
        context.getSharedPreferences("viptv_smartcast_credentials", Context.MODE_PRIVATE),
        { androidKey() },
    )

    override fun load(tvOrigin: String): String? {
        val encoded = preferences.getString(entry(tvOrigin), null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override fun save(tvOrigin: String, token: String) {
        require(token.isNotEmpty() && token.length <= 1024 && token.none { it.isISOControl() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keyProvider()) }
        val sealed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(entry(tvOrigin), Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
    }

    override fun clear(tvOrigin: String) {
        preferences.edit().remove(entry(tvOrigin)).apply()
    }

    private fun entry(tvOrigin: String): String = MessageDigest.getInstance("SHA-256")
        .digest(tvOrigin.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_ALIAS = "viptv.smartcast.credentials.v1"

        fun androidKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                generateKey()
            }
        }
    }
}