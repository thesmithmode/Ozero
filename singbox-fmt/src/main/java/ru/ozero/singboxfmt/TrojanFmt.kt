package ru.ozero.singboxfmt

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object TrojanFmt {

    fun parse(uri: String): TrojanBean {
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
        bean.allowInsecure =
            parsed.getQueryParameter("allowInsecure") == "1"
        V2RayFmtUtils.parseTransportParams(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }
}
