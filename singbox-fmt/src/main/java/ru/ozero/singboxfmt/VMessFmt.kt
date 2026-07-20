package ru.ozero.singboxfmt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONObject

object VMessFmt {

    private val TRUTHY_JSON_VALUES = setOf("1", "true", "yes")
    private val FALSEY_JSON_VALUES = setOf("0", "false", "no")

    fun parse(uri: String): VMessBean {
        require(uri.startsWith("vmess://")) { "Not a vmess:// URI" }
        val payload = uri.removePrefix("vmess://")
        val json = V2RayFmtUtils.tryBase64Decode(payload)
        if (json != null && json.trimStart().startsWith("{")) {
            return parseJson(json)
        }
        return parseStd(UriCompat.parse(uri))
    }

    private fun parseJson(raw: String): VMessBean {
        val j = JSONObject(raw)
        val bean = VMessBean()
        bean.serverAddress = j.optString("add", "127.0.0.1")
        bean.serverPort = j.optString("port", "443").toIntOrNull() ?: 443
        bean.uuid = j.optString("id", "")
        bean.alterId = j.optString("aid", "0").toIntOrNull() ?: 0
        bean.encryption = j.optString("scy", "auto").ifEmpty { "auto" }
        bean.type = V2RayFmtUtils.mapTransportType(j.optString("net", "tcp"))
        bean.host = j.optString("host", "")
        bean.path = j.optString("path", "")
        bean.name = j.optString("ps", "")
        bean.headerType = j.optString("type", "none")
        val tls = j.optString("tls", "")
        bean.security = if (tls == "tls") "tls" else "none"
        bean.sni = j.optString("sni", "")
        bean.alpn = j.optString("alpn", "")
        bean.utlsFingerprint = j.optString("fp", "")
        bean.allowInsecure = jsonBoolean(
            j,
            "allowInsecure",
            "allow-insecure",
            "allow_insecure",
            "insecure",
            "skip-cert-verify",
            "skipCertVerify",
            "skip_cert_verify",
        )
        bean.initializeDefaultValues()
        return bean
    }

    private fun jsonBoolean(j: JSONObject, vararg keys: String): Boolean {
        var resolved: Boolean? = null
        for (key in keys) {
            if (j.has(key)) resolved = jsonBooleanValue(j.opt(key)) ?: resolved
        }
        return resolved ?: false
    }

    private fun jsonBooleanValue(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.lowercase(Locale.ROOT)) {
            in TRUTHY_JSON_VALUES -> true
            in FALSEY_JSON_VALUES -> false
            else -> null
        }
        else -> null
    }

    private fun parseStd(parsed: UriCompat): VMessBean {
        val bean = VMessBean()
        bean.uuid = parsed.userInfo ?: error("VMess URI missing UUID")
        bean.serverAddress = parsed.host ?: error("VMess URI missing host")
        bean.serverPort = parsed.port.takeIf { it > 0 } ?: 443
        bean.name = parsed.fragment?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        } ?: ""
        bean.encryption = parsed.getQueryParameter("encryption") ?: "auto"
        V2RayFmtUtils.parseSecurityParams(bean, parsed)
        V2RayFmtUtils.parseTransportParams(bean, parsed)
        bean.initializeDefaultValues()
        return bean
    }
}
