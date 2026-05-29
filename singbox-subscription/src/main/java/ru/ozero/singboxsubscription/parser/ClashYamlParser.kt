package ru.ozero.singboxsubscription.parser

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean

object ClashYamlParser {
    private val yaml = Yaml(SafeConstructor(LoaderOptions()))

    fun parse(text: String): List<AbstractBean> {
        val root = loadRoot(text) ?: return emptyList()
        val proxies = root["proxies"].listValue()
        return proxies.mapNotNull { (it as? Map<*, *>)?.toStringKeyMap()?.let(::parseProxy) }
    }

    private fun loadRoot(text: String): Map<String, Any?>? = try {
        (yaml.load<Any?>(text) as? Map<*, *>)?.toStringKeyMap()
    } catch (_: YAMLException) {
        null
    }

    private fun parseProxy(fields: Map<String, Any?>): AbstractBean? = when (fields.string("type").lowercase()) {
        "vless" -> VLESSBean().apply {
            applyCommon(fields)
            uuid = fields.string("uuid")
            encryption = fields.string("encryption").ifBlank { "none" }
            flow = fields.string("flow")
            applyV2Ray(fields)
        }
        "vmess" -> VMessBean().apply {
            applyCommon(fields)
            uuid = fields.string("uuid")
            alterId = fields.string("alterId", "alter-id", "alter_id", "aid").toIntOrNull() ?: 0
            encryption = fields.string("cipher", "security").ifBlank { "auto" }
            applyV2Ray(fields)
        }
        "trojan" -> TrojanBean().apply {
            applyCommon(fields)
            password = fields.string("password")
            applyV2Ray(fields)
        }
        "ss", "shadowsocks" -> ShadowsocksBean().apply {
            applyCommon(fields)
            method = fields.string("cipher", "method").ifBlank { method }
            password = fields.string("password")
            plugin = fields.string("plugin")
            pluginOpts = fields.string("plugin-opts", "plugin_opts")
        }
        else -> null
    }?.takeIf { it.serverAddress.isNotBlank() && it.serverPort > 0 }

    private fun AbstractBean.applyCommon(fields: Map<String, Any?>) {
        name = fields.string("name")
        serverAddress = fields.string("server")
        serverPort = fields.int("port") ?: 0
    }

    private fun StandardV2RayBean.applyV2Ray(fields: Map<String, Any?>) {
        type = normalizeNetwork(fields.string("network", "net"))
        security = when {
            fields.bool("reality") -> "reality"
            fields.bool("tls") -> "tls"
            else -> fields.string("security").ifBlank { "none" }
        }
        sni = fields.string("servername", "sni")
        alpn = fields.listString("alpn")
        allowInsecure = fields.bool("skip-cert-verify") || fields.bool("allowInsecure")
        utlsFingerprint = fields.string("client-fingerprint", "fingerprint", "fp")
        applyNestedTransport(fields)
        applyReality(fields)
        initializeDefaultValues()
    }

    private fun StandardV2RayBean.applyNestedTransport(fields: Map<String, Any?>) {
        val ws = fields.mapValue("ws-opts", "ws_opts")
        val grpc = fields.mapValue("grpc-opts", "grpc_opts")
        val http = fields.mapValue("h2-opts", "h2_opts", "http-opts", "http_opts")
        val httpUpgrade = fields.mapValue("httpupgrade-opts", "httpupgrade_opts")
        path = fields.string("path").ifBlank {
            ws.string("path").ifBlank { http.string("path").ifBlank { httpUpgrade.string("path") } }
        }
        host = fields.string("host").ifBlank {
            ws.mapValue("headers").string("Host", "host").ifBlank {
                ws.string("Host", "host").ifBlank { http.listString("host").ifBlank { httpUpgrade.string("host") } }
            }
        }
        grpcServiceName = fields.string("serviceName", "service-name").ifBlank { grpc.string("grpc-service-name") }
    }

    private fun StandardV2RayBean.applyReality(fields: Map<String, Any?>) {
        val reality = fields.mapValue("reality-opts", "reality_opts")
        realityPublicKey = fields.string("pbk", "public-key").ifBlank { reality.string("public-key") }
        realityShortId = fields.string("sid", "short-id").ifBlank { reality.string("short-id") }
        realityFingerprint = utlsFingerprint.ifBlank { realityFingerprint }
    }

    private fun normalizeNetwork(network: String): String = when (network.lowercase()) {
        "h2" -> "http"
        "xhttp" -> "splithttp"
        "" -> "tcp"
        else -> network.lowercase()
    }

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> = entries.associate { (key, value) ->
        key.toString() to value
    }

    private fun Map<String, Any?>.string(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            null -> null
            is Map<*, *> -> value.entries.joinToString(";") { (k, v) -> "$k=$v" }
            is Iterable<*> -> value.joinToString(",") { it.toString() }
            else -> value.toString()
        }?.takeIf { it.isNotBlank() }
    }.orEmpty()

    private fun Map<String, Any?>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun Map<String, Any?>.bool(key: String): Boolean = when (val value = this[key]) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.lowercase() in setOf("true", "1", "yes")
        else -> false
    }

    private fun Map<String, Any?>.mapValue(vararg keys: String): Map<String, Any?> = keys.firstNotNullOfOrNull { key ->
        (this[key] as? Map<*, *>)?.toStringKeyMap()
    }.orEmpty()

    private fun Any?.listValue(): List<Any?> = when (this) {
        is List<*> -> this
        else -> emptyList()
    }

    private fun Map<String, Any?>.listString(key: String): String = when (val value = this[key]) {
        is Iterable<*> -> value.joinToString(",") { it.toString() }
        else -> string(key)
    }
}
