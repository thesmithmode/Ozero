package ru.ozero.singboxfmt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ShadowsocksFmt {

    fun parse(uri: String): ShadowsocksBean {
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
            parseUserInfo(bean, mainPart.substring(0, atIdx))
            parseServerPart(bean, mainPart.substring(atIdx + 1))
        } else {
            val decoded = V2RayFmtUtils.tryBase64Decode(mainPart.split('?')[0])
                ?: error("SS URI: cannot decode base64")
            val sepIdx = decoded.lastIndexOf('@')
            if (sepIdx >= 0) {
                parseUserInfo(bean, decoded.substring(0, sepIdx))
                parseServerPart(bean, decoded.substring(sepIdx + 1))
            } else {
                parseMethodPassword(bean, decoded)
            }
        }
        bean.initializeDefaultValues()
        return bean
    }

    private fun parseUserInfo(bean: ShadowsocksBean, userInfo: String) {
        val decoded = V2RayFmtUtils.tryBase64Decode(userInfo) ?: userInfo
        parseMethodPassword(bean, decoded)
    }

    private fun parseMethodPassword(bean: ShadowsocksBean, decoded: String) {
        val colonIdx = decoded.indexOf(':')
        if (colonIdx >= 0) {
            bean.method = decoded.substring(0, colonIdx)
            bean.password = decoded.substring(colonIdx + 1)
        }
    }

    private fun parseServerPart(bean: ShadowsocksBean, serverPart: String) {
        val queryIdx = serverPart.indexOf('?')
        val hostPort = if (queryIdx >= 0) {
            serverPart.substring(0, queryIdx)
        } else {
            serverPart
        }
        val lastColon = hostPort.lastIndexOf(':')
        if (lastColon >= 0) {
            bean.serverAddress = hostPort.substring(0, lastColon)
                .removeSurrounding("[", "]")
            bean.serverPort =
                hostPort.substring(lastColon + 1).toIntOrNull() ?: 443
        }
        if (queryIdx >= 0) {
            val parsed = UriCompat.parse(
                "ss://x@x:0?${serverPart.substring(queryIdx + 1)}",
            )
            bean.plugin = parsed.getQueryParameter("plugin") ?: ""
        }
    }
}
