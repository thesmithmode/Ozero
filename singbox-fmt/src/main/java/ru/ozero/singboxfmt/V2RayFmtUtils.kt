package ru.ozero.singboxfmt

import java.util.Base64 as JBase64

internal object V2RayFmtUtils {

    fun mapTransportType(raw: String): String = when (raw) {
        "h2" -> "http"
        "xhttp" -> "splithttp"
        else -> raw
    }

    fun parseSecurityParams(bean: StandardV2RayBean, parsed: UriCompat) {
        bean.security = parsed.getQueryParameter("security") ?: "none"
        bean.sni = parsed.firstQueryParameter("sni", "serverName", "servername", "server_name") ?: ""
        bean.alpn = parsed.getQueryParameter("alpn") ?: ""
        bean.utlsFingerprint = parsed.getQueryParameter("fp") ?: ""
        bean.realityPublicKey = parsed.getQueryParameter("pbk") ?: ""
        bean.realityShortId = parsed.getQueryParameter("sid") ?: ""
        bean.realityFingerprint = parsed.getQueryParameter("fp") ?: "chrome"
        bean.allowInsecure = listOf(
            "allowInsecure",
            "allow-insecure",
            "allow_insecure",
            "insecure",
            "skip-cert-verify",
            "skipCertVerify",
            "skip_cert_verify",
        )
            .any { parsed.getQueryParameter(it).isTruthy() }
    }

    fun parseTransportParams(bean: StandardV2RayBean, parsed: UriCompat) {
        bean.type = mapTransportType(parsed.getQueryParameter("type") ?: "tcp")
        when (bean.type) {
            "ws", "httpupgrade" -> {
                bean.host = parsed.getQueryParameter("host") ?: ""
                bean.path = parsed.getQueryParameter("path") ?: "/"
            }
            "http" -> {
                bean.host = parsed.getQueryParameter("host") ?: ""
                bean.path = parsed.getQueryParameter("path") ?: "/"
            }
            "grpc" -> {
                bean.grpcServiceName =
                    parsed.getQueryParameter("serviceName") ?: ""
            }
            "tcp" -> parseTcpParams(bean, parsed)
        }
    }

    fun parseTcpParams(bean: StandardV2RayBean, parsed: UriCompat) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        if (bean.headerType == "http") {
            bean.host = parsed.getQueryParameter("host") ?: ""
            bean.path = parsed.getQueryParameter("path") ?: "/"
        }
    }

    fun tryBase64Decode(text: String): String? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val padded = padBase64(cleaned)
        return runCatching {
            JBase64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
        }.getOrNull() ?: runCatching {
            JBase64.getMimeDecoder().decode(padded).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun padBase64(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }

    private fun String?.isTruthy(): Boolean = when (this?.lowercase()) {
        "1", "true", "yes" -> true
        else -> false
    }

    private fun UriCompat.firstQueryParameter(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { getQueryParameter(it)?.takeIf(String::isNotBlank) }
}
