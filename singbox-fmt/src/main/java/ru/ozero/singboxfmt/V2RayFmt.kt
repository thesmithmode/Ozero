package ru.ozero.singboxfmt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object V2RayFmt {

    fun parseVLESS(uri: String): VLESSBean {
        require(uri.startsWith("vless://")) { "Not a vless:// URI" }
        val parsed = UriCompat.parse(uri)
        val bean = VLESSBean()
        parseBasicParams(bean, parsed)
        V2RayFmtUtils.parseSecurityParams(bean, parsed)
        parseTransportParams(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }

    fun parseVMess(uri: String): VMessBean = VMessFmt.parse(uri)
    fun parseTrojan(uri: String): TrojanBean = TrojanFmt.parse(uri)
    fun parseShadowsocks(uri: String): ShadowsocksBean =
        ShadowsocksFmt.parse(uri)

    private fun parseBasicParams(bean: VLESSBean, parsed: UriCompat) {
        bean.uuid = parsed.userInfo ?: error("VLESS URI missing UUID")
        bean.serverAddress = parsed.host ?: error("VLESS URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        bean.flow = parsed.getQueryParameter("flow") ?: ""
        bean.encryption = parsed.getQueryParameter("encryption") ?: "none"
    }

    private fun parseTransportParams(bean: VLESSBean, parsed: UriCompat) {
        bean.type = V2RayFmtUtils.mapTransportType(
            parsed.getQueryParameter("type") ?: "tcp",
        )
        when (bean.type) {
            "ws", "httpupgrade" -> parseWsParams(bean, parsed)
            "http" -> parseHttpParams(bean, parsed)
            "grpc" -> {
                bean.grpcServiceName =
                    parsed.getQueryParameter("serviceName") ?: ""
            }
            "splithttp" -> parseSplithttpParams(bean, parsed)
            "kcp", "mkcp" -> parseKcpParams(bean, parsed)
            "quic" -> parseQuicParams(bean, parsed)
            "tcp" -> V2RayFmtUtils.parseTcpParams(bean, parsed)
        }
    }

    private fun parseWsParams(bean: VLESSBean, parsed: UriCompat) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
        bean.earlyDataHeaderName = parsed.getQueryParameter("ed") ?: ""
    }

    private fun parseHttpParams(bean: VLESSBean, parsed: UriCompat) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
    }

    private fun parseSplithttpParams(bean: VLESSBean, parsed: UriCompat) {
        bean.host = parsed.getQueryParameter("host") ?: ""
        bean.path = parsed.getQueryParameter("path") ?: "/"
        bean.splithttpMode = parsed.getQueryParameter("mode") ?: "auto"
    }

    private fun parseKcpParams(bean: VLESSBean, parsed: UriCompat) {
        bean.type = "kcp"
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        bean.mKcpSeed = parsed.getQueryParameter("seed") ?: ""
    }

    private fun parseQuicParams(bean: VLESSBean, parsed: UriCompat) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        bean.quicSecurity =
            parsed.getQueryParameter("quicSecurity") ?: "none"
        bean.quicKey = parsed.getQueryParameter("key") ?: ""
    }
}
