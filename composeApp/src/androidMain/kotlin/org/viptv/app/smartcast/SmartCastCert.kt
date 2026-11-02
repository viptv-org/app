package org.viptv.app.smartcast

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.X509TrustManager

fun certificateFingerprint(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }

internal class SmartCastTrustManager(
    private val pinnedFingerprint: String?,
) : X509TrustManager {
    private val observed = AtomicReference<String?>()

    fun observedFingerprint(): String? = observed.get()

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("SmartCast peer presented no certificate")
        val fingerprint = certificateFingerprint(leaf)
        observed.set(fingerprint)
        if (pinnedFingerprint != null && fingerprint != pinnedFingerprint) {
            throw CertificateException("SmartCast certificate changed; pair this TV again")
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}