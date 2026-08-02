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
import ru.ozero.singboxfmt.canonicalBeanOrThrow
import ru.ozero.singboxfmt.hasRequiredOutboundCredentials
import ru.ozero.singboxfmt.normalizeSingboxTransport

object ClashYamlParser {
    private const val MAX_YAML_CODE_POINTS = 16 * 1024 * 1024
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535
    private val proxiesKeyPattern = Regex("""(?m)^\s*proxies\s*:""")

    fun parse(text: String): List<AbstractBean> {
        if (!proxiesKeyPattern.containsMatchIn(text)) return emptyList()
        val root = try {
            val loaderOptions = LoaderOptions().apply {
                codePointLimit = MAX_YAML_CODE_POINTS
            }
            when (val loaded = Yaml(SafeConstructor(loaderOptions)).load<Any?>(text)) {
                is Map<*, *> -> loaded.toStringKeyMap()
                else -> null
            }
        } catch (_: YAMLException) {
            null
        } ?: return emptyList()
        val proxies = when (val value = root["proxies"]) {
            is List<*> -> value
            else -> return emptyList()
        }
        return proxies.mapNotNull { item ->
            when (item) {
                is Map<*, *> -> parseProxy(item.toStringKeyMap())
                else -> null
            }
        }.map { it.canonicalBeanOrThrow() }
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
            applyV2Ray(fields, defaultSecurity = "tls")
        }
        "ss", "shadowsocks" -> ShadowsocksBean().apply {
            applyCommon(fields)
            method = fields.shadowsocksMethod().ifBlank { method }
            password = fields.string("password")
            plugin = fields.string("plugin")
            pluginOpts = fields.string("plugin-opts", "plugin_opts", entrySeparator = ";")
        }
        else -> null
    }?.takeIf {
        it.serverAddress.isNotBlank() &&
            it.serverPort in MIN_PORT..MAX_PORT &&
            it.hasRequiredOutboundCredentials()
    }

    private fun AbstractBean.applyCommon(fields: Map<String, Any?>) {
        name = fields.string("name")
        serverAddress = fields.string("server")
        serverPort = fields.int("port") ?: 0
    }

    private fun StandardV2RayBean.applyV2Ray(fields: Map<String, Any?>, defaultSecurity: String = "none") {
        rawTransportType = fields.string("network", "net")
        type = normalizeSingboxTransport(rawTransportType)
        val reality = fields.mapValue("reality-opts", "reality_opts")
        val realityPublicKey = fields.string(
            "pbk",
            "publicKey",
            "public-key",
            "public_key",
            "realityPublicKey",
            "reality_public_key",
        ).ifBlank {
            reality.string(
                "pbk",
                "publicKey",
                "public-key",
                "public_key",
                "realityPublicKey",
                "reality_public_key",
            )
        }
        val realityShortId = fields.string(
            "sid",
            "shortId",
            "short-id",
            "short_id",
            "realityShortId",
            "reality_short_id",
        ).ifBlank {
            reality.string("sid", "shortId", "short-id", "short_id", "realityShortId", "reality_short_id")
        }
        val explicitSecurity = fields.string("security")
        security = when {
            fields.bool("reality") || realityPublicKey.isNotBlank() || realityShortId.isNotBlank() -> "reality"
            fields.bool("tls") -> "tls"
            explicitSecurity.isNotBlank() -> explicitSecurity
            fields.containsKey("tls") -> "none"
            else -> defaultSecurity
        }
        sni = fields.string("sni", "serverName", "servername", "server_name", "peer")
        alpn = fields.listString("alpn")
        allowInsecure = fields.bool("skip-cert-verify") || fields.bool("allowInsecure")
        utlsFingerprint = fields.string("fp", "fingerprint", "client-fingerprint", "client_fingerprint")
        applyNestedTransport(fields)
        applyReality(fields, realityPublicKey, realityShortId)
        initializeDefaultValues()
    }

    private fun StandardV2RayBean.applyNestedTransport(fields: Map<String, Any?>) {
        val ws = fields.mapValue("ws-opts", "ws_opts")
        val grpc = fields.mapValue("grpc-opts", "grpc_opts")
        val http = fields.mapValue("h2-opts", "h2_opts", "http-opts", "http_opts")
        val httpUpgrade = fields.mapValue("httpupgrade-opts", "httpupgrade_opts")
        rawTransportType = fields.string("network", "net").ifBlank {
            when {
                ws.isNotEmpty() -> "ws"
                grpc.isNotEmpty() -> "grpc"
                http.isNotEmpty() -> "http"
                httpUpgrade.isNotEmpty() -> "httpupgrade"
                else -> rawTransportType
            }
        }
        type = normalizeSingboxTransport(rawTransportType)
        path = fields.string("path").ifBlank {
            ws.string("path").ifBlank { http.string("path").ifBlank { httpUpgrade.string("path") } }
        }
        host = fields.string("host").ifBlank {
            ws.mapValue("headers").string("Host", "host").ifBlank {
                ws.string("Host", "host").ifBlank { http.listString("host").ifBlank { httpUpgrade.string("host") } }
            }
        }
        grpcServiceName = fields.string(
            "serviceName",
            "service-name",
            "service_name",
            "grpc-service-name",
            "grpc_service_name",
        ).ifBlank {
            grpc.string("serviceName", "service-name", "service_name", "grpc-service-name", "grpc_service_name")
        }
    }

    private fun StandardV2RayBean.applyReality(
        fields: Map<String, Any?>,
        publicKey: String,
        shortId: String,
    ) {
        realityPublicKey = publicKey
        realityShortId = shortId
        realityFingerprint = fields.string("fp", "fingerprint", "client-fingerprint", "client_fingerprint")
            .ifBlank { realityFingerprint }
    }

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> = entries.associate { (key, value) ->
        key.toString() to value
    }

    private fun Map<String, Any?>.string(
        vararg keys: String,
        entrySeparator: String = ",",
    ): String = keys.firstNotNullOfOrNull { key ->
        when (val value = this[key]) {
            null -> null
            is Map<*, *> -> value.entries.joinToString(entrySeparator) { (k, v) -> "$k=$v" }
            is Iterable<*> -> value.joinToString(entrySeparator) { it.toString() }
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
        when (val value = this[key]) {
            is Map<*, *> -> value.toStringKeyMap()
            else -> null
        }
    }.orEmpty()

    private fun Map<String, Any?>.listString(key: String): String = when (val value = this[key]) {
        is Iterable<*> -> value.joinToString(",") { it.toString() }
        else -> string(key)
    }

    private fun Map<String, Any?>.shadowsocksMethod(): String = when (val cipher = this["cipher"]) {
        is String -> cipher
        is Number,
        is Boolean -> cipher.toString()
        is Map<*, *> -> {
            val parsed = cipher.toStringKeyMap()
            val method = parsed["method"]?.toString()?.trim()
            method?.takeIf { it.isNotBlank() } ?: parsed.entries.joinToString(",") { (key, value) -> "$key=$value" }
        }
        else -> string("method")
    }.ifBlank { string("method") }
}
