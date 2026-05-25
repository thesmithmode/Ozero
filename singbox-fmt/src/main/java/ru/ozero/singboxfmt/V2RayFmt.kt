package ru.ozero.singboxfmt

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

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

    private fun parseSecurityParams(bean: StandardV2RayBean, parsed: Uri) {
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

    private fun parseTcpParams(bean: StandardV2RayBean, parsed: Uri) {
        bean.headerType = parsed.getQueryParameter("headerType") ?: "none"
        if (bean.headerType == "http") {
            bean.host = parsed.getQueryParameter("host") ?: ""
            bean.path = parsed.getQueryParameter("path") ?: "/"
        }
    }

    fun parseVMess(uri: String): VMessBean {
        require(uri.startsWith("vmess://")) { "Not a vmess:// URI" }
        val payload = uri.removePrefix("vmess://")
        val json = tryBase64Decode(payload)
        if (json != null && json.trimStart().startsWith("{")) {
            return parseVMessJson(json)
        }
        return parseVMessStd(Uri.parse(uri))
    }

    private fun parseVMessJson(raw: String): VMessBean {
        val j = JSONObject(raw)
        val bean = VMessBean()
        bean.serverAddress = j.optString("add", "127.0.0.1")
        bean.serverPort = j.optString("port", "443").toIntOrNull() ?: 443
        bean.uuid = j.optString("id", "")
        bean.alterId = j.optString("aid", "0").toIntOrNull() ?: 0
        bean.encryption = j.optString("scy", "auto").ifEmpty { "auto" }
        bean.type = mapTransportType(j.optString("net", "tcp"))
        bean.host = j.optString("host", "")
        bean.path = j.optString("path", "")
        bean.name = j.optString("ps", "")
        bean.headerType = j.optString("type", "none")
        val tls = j.optString("tls", "")
        bean.security = if (tls == "tls") "tls" else "none"
        bean.sni = j.optString("sni", "")
        bean.alpn = j.optString("alpn", "")
        bean.utlsFingerprint = j.optString("fp", "")
        bean.initializeDefaultValues()
        return bean
    }

    private fun parseVMessStd(parsed: Uri): VMessBean {
        val bean = VMessBean()
        bean.uuid = parsed.userInfo ?: error("VMess URI missing UUID")
        bean.serverAddress = parsed.host ?: error("VMess URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        bean.encryption = parsed.getQueryParameter("encryption") ?: "auto"
        parseSecurityParams(bean, parsed)
        parseTransportParamsV2Ray(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }

    fun parseTrojan(uri: String): TrojanBean {
        require(uri.startsWith("trojan://")) { "Not a trojan:// URI" }
        val parsed = Uri.parse(uri)
        val bean = TrojanBean()
        bean.password = parsed.userInfo ?: error("Trojan URI missing password")
        bean.serverAddress = parsed.host ?: error("Trojan URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        bean.security = parsed.getQueryParameter("security") ?: "tls"
        bean.sni = parsed.getQueryParameter("sni") ?: ""
        bean.alpn = parsed.getQueryParameter("alpn") ?: ""
        bean.utlsFingerprint = parsed.getQueryParameter("fp") ?: ""
        bean.allowInsecure = parsed.getQueryParameter("allowInsecure") == "1"
        parseTransportParamsV2Ray(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }

    fun parseShadowsocks(uri: String): ShadowsocksBean {
        require(uri.startsWith("ss://")) { "Not a ss:// URI" }
        val bean = ShadowsocksBean()
        val withoutScheme = uri.removePrefix("ss://")
        val fragmentIdx = withoutScheme.indexOf('#')
        if (fragmentIdx >= 0) {
            bean.name = URLDecoder.decode(
                withoutScheme.substring(fragmentIdx + 1),
                StandardCharsets.UTF_8.name(),
            )
        }
        val mainPart = if (fragmentIdx >= 0) {
            withoutScheme.substring(0, fragmentIdx)
        } else {
            withoutScheme
        }

        val atIdx = mainPart.lastIndexOf('@')
        if (atIdx >= 0) {
            val userInfo = mainPart.substring(0, atIdx)
            val serverPart = mainPart.substring(atIdx + 1)
            parseSsUserInfo(bean, userInfo)
            parseSsServerPart(bean, serverPart)
        } else {
            val decoded = tryBase64Decode(mainPart.split('?')[0])
                ?: error("SS URI: cannot decode base64")
            val colonIdx = decoded.lastIndexOf('@')
            if (colonIdx >= 0) {
                parseSsUserInfo(bean, decoded.substring(0, colonIdx))
                parseSsServerPart(bean, decoded.substring(colonIdx + 1))
            } else {
                parseSsMethodPassword(bean, decoded)
            }
        }
        bean.initializeDefaultValues()
        return bean
    }

    private fun parseSsUserInfo(bean: ShadowsocksBean, userInfo: String) {
        val decoded = tryBase64Decode(userInfo) ?: userInfo
        parseSsMethodPassword(bean, decoded)
    }

    private fun parseSsMethodPassword(bean: ShadowsocksBean, decoded: String) {
        val colonIdx = decoded.indexOf(':')
        if (colonIdx >= 0) {
            bean.method = decoded.substring(0, colonIdx)
            bean.password = decoded.substring(colonIdx + 1)
        }
    }

    private fun parseSsServerPart(bean: ShadowsocksBean, serverPart: String) {
        val queryIdx = serverPart.indexOf('?')
        val hostPort = if (queryIdx >= 0) serverPart.substring(0, queryIdx) else serverPart
        val lastColon = hostPort.lastIndexOf(':')
        if (lastColon >= 0) {
            bean.serverAddress = hostPort.substring(0, lastColon)
                .removeSurrounding("[", "]")
            bean.serverPort = hostPort.substring(lastColon + 1).toIntOrNull() ?: 443
        }
        if (queryIdx >= 0) {
            val parsed = Uri.parse("ss://x@x:0?${serverPart.substring(queryIdx + 1)}")
            bean.plugin = parsed.getQueryParameter("plugin") ?: ""
        }
    }

    private fun parseTransportParamsV2Ray(bean: StandardV2RayBean, parsed: Uri) {
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
            "grpc" -> bean.grpcServiceName = parsed.getQueryParameter("serviceName") ?: ""
            "tcp" -> parseTcpParams(bean, parsed)
        }
    }

    private fun tryBase64Decode(text: String): String? {
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
