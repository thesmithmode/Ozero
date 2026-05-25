package ru.ozero.singboxfmt

import android.net.Uri
import android.util.Base64

internal object V2RayFmtUtils {

    fun mapTransportType(raw: String): String = when (raw) {
        "h2" -> "http"
        "xhttp" -> "splithttp"
        else -> raw
    }

    fun parseSecurityParams(bean: StandardV2RayBean, parsed: Uri) {
        bean.security = parsed.getQueryParameter("security") ?: "none"
        bean.sni = parsed.getQueryParameter("sni") ?: ""
        bean.alpn = parsed.getQueryParameter("alpn") ?: ""
        bean.utlsFingerprint = parsed.getQueryParameter("fp") ?: ""
        bean.realityPublicKey = parsed.getQueryParameter("pbk") ?: ""
        bean.realityShortId = parsed.getQueryParameter("sid") ?: ""
        bean.realityFingerprint = parsed.getQueryParameter("fp") ?: "chrome"
    }

    fun parseTransportParams(bean: StandardV2RayBean, parsed: Uri) {
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

    fun parseTcpParams(bean: StandardV2RayBean, parsed: Uri) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        if (bean.headerType == "http") {
            bean.host = parsed.getQueryParameter("host") ?: ""
            bean.path = parsed.getQueryParameter("path") ?: "/"
        }
    }

    fun tryBase64Decode(text: String): String? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        return runCatching {
            Base64.decode(cleaned, Base64.URL_SAFE or Base64.NO_WRAP)
                .toString(Charsets.UTF_8)
        }.getOrNull() ?: runCatching {
            Base64.decode(cleaned, Base64.DEFAULT)
                .toString(Charsets.UTF_8)
        }.getOrNull()
    }
}
