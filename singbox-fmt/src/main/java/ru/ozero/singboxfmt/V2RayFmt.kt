package ru.ozero.singboxfmt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object V2RayFmt {
    private val transportAliases = mapOf("h2" to "http", "xhttp" to "splithttp")

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
        val rawType = parsed.firstQueryParameter("type", "network", "net") ?: "tcp"
        bean.type = transportAliases[rawType.trim().lowercase()] ?: V2RayFmtUtils.mapTransportType(rawType)
        when (bean.type) {
            "ws", "httpupgrade" -> parseWsParams(bean, parsed)
            "http" -> parseHttpParams(bean, parsed)
            "grpc" -> {
                bean.grpcServiceName =
                    parsed.firstQueryParameter(
                        "serviceName",
                        "service-name",
                        "service_name",
                        "grpc-service-name",
                        "grpc_service_name",
                    ) ?: ""
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
        bean.maxEarlyData = parsed.getQueryParameter("ed")?.toIntOrNull() ?: 0
        bean.earlyDataHeaderName = parsed.getQueryParameter("eh") ?: ""
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
        bean.headerType = parsed.firstQueryParameter("headerType", "header-type", "header_type") ?: "none"
        bean.mKcpSeed = parsed.getQueryParameter("seed") ?: ""
    }

    private fun parseQuicParams(bean: VLESSBean, parsed: UriCompat) {
        bean.headerType = parsed.firstQueryParameter("headerType", "header-type", "header_type") ?: "none"
        bean.quicSecurity =
            parsed.getQueryParameter("quicSecurity") ?: "none"
        bean.quicKey = parsed.getQueryParameter("key") ?: ""
    }
}
