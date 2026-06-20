package ru.ozero.singboxfmt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object TrojanFmt {

    fun parse(uri: String): TrojanBean {
        require(uri.startsWith("trojan://")) { "Not a trojan:// URI" }
        val parsed = UriCompat.parse(uri)
        val bean = TrojanBean()
        bean.password = parsed.userInfo ?: error("Trojan URI missing password")
        bean.serverAddress = parsed.host ?: error("Trojan URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        V2RayFmtUtils.parseSecurityParams(bean, parsed, defaultSecurity = "tls")
        V2RayFmtUtils.parseTransportParams(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }
}
