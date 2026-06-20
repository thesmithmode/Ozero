package ru.ozero.singboxprocess

import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal class TrustAnchorPemReader(
    private val encode: (ByteArray) -> String,
) {
    fun read(trustManagers: Array<TrustManager>): List<String> = trustManagers
        .filterIsInstance<X509TrustManager>()
        .flatMap { manager -> manager.acceptedIssuers.toList() }
        .distinctBy { certificate -> certificate.encoded.contentHashCode() }
        .map { certificate -> certificate.toPem() }

    private fun X509Certificate.toPem(): String {
        val body = encode(encoded)
        return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
    }
}
