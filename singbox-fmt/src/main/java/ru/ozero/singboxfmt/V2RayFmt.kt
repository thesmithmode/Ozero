package ru.ozero.singboxfmt

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object V2RayFmt {

    fun parseVLESS(uri: String): VLESSBean {
        require(uri.startsWith("vless://")) { "Not a vless:// URI" }
        val parsed = Uri.parse(uri)
        val bean = VLESSBean()
        parseBasicParams(bean, parsed)
        parseSecurityParams(bean, parsed)
        parseTransportParams(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }

    private fun parseBasicParams(bean: VLESSBean, parsed: Uri) {
        bean.uuid = parsed.userInfo ?: error("VLESS URI missing UUID")
        bean.serverAddress = parsed.host ?: error("VLESS URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        bean.flow = parsed.getQueryParameter("flow") ?: ""
        bean.encryption = parsed.getQueryParameter("encryption") ?: "none"
    }

    private fun parseSecurityParams(bean: VLESSBean, parsed: Uri) {
        bean.security = parsed.getQueryParameter("security") ?: "none"
        bean.sni = parsed.getQueryParameter("sni") ?: ""
        bean.alpn = parsed.getQueryParameter("alpn") ?: ""
        bean.utlsFingerprint = parsed.getQueryParameter("fp") ?: ""
        bean.realityPublicKey = parsed.getQueryParameter("pbk") ?: ""
        bean.realityShortId = parsed.getQueryParameter("sid") ?: ""
        bean.realityFingerprint = parsed.getQueryParameter("fp") ?: "chrome"
    }

    private fun mapTransportType(raw: String): String = when (raw) {
        "h2" -> "http"
        "xhttp" -> "splithttp"
        else -> raw
    }

    private fun parseTransportParams(bean: VLESSBean, parsed: Uri) {
        bean.type = mapTransportType(parsed.getQueryParameter("type") ?: "tcp")
        when (bean.type) {
            "ws", "httpupgrade" -> parseWsParams(bean, parsed)
            "http" -> parseHttpParams(bean, parsed)
            "grpc" -> bean.grpcServiceName = parsed.getQueryParameter("serviceName") ?: ""
            "splithttp" -> parseSplithttpParams(bean, parsed)
            "kcp", "mkcp" -> parseKcpParams(bean, parsed)
            "quic" -> parseQuicParams(bean, parsed)
            "tcp" -> parseTcpParams(bean, parsed)
        }
    }

    private fun parseWsParams(bean: VLESSBean, parsed: Uri) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
        bean.earlyDataHeaderName = parsed.getQueryParameter("ed") ?: ""
    }

    private fun parseHttpParams(bean: VLESSBean, parsed: Uri) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
    }

    private fun parseSplithttpParams(bean: VLESSBean, parsed: Uri) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
        bean.splithttpMode = parsed.getQueryParameter("mode") ?: "auto"
    }

    private fun parseKcpParams(bean: VLESSBean, parsed: Uri) {
        bean.type = "kcp"
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        bean.mKcpSeed = parsed.getQueryParameter("seed") ?: ""
    }

    private fun parseQuicParams(bean: VLESSBean, parsed: Uri) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        bean.quicSecurity = parsed.getQueryParameter("quicSecurity") ?: "none"
        bean.quicKey = parsed.getQueryParameter("key") ?: ""
    }

    private fun parseTcpParams(bean: VLESSBean, parsed: Uri) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        if (bean.headerType == "http") {
            bean.host = parsed.getQueryParameter("host") ?: ""
            bean.path = parsed.getQueryParameter("path") ?: "/"
        }
    }
}
