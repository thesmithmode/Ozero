package ru.ozero.singboxsubscription.parser

import org.json.JSONObject
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.V2RayFmt
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.canonicalBeanOrThrow
import ru.ozero.singboxfmt.hasRequiredOutboundCredentials
import ru.ozero.singboxfmt.normalizeSingboxTransport

object RawShareLinksParser {
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    fun parse(text: String): List<AbstractBean> {
        val links = parseShareLinks(text)
        return links.ifEmpty { parseSingboxJson(text) }
            .ifEmpty { ClashYamlParser.parse(text) }
            .map { it.canonicalBeanOrThrow() }
    }

    private fun parseShareLinks(text: String): List<AbstractBean> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap(::extractShareLinks)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                runCatching {
                    when {
                        token.startsWith("vless://") -> V2RayFmt.parseVLESS(token)
                        token.startsWith("vmess://") -> V2RayFmt.parseVMess(token)
                        token.startsWith("trojan://") -> V2RayFmt.parseTrojan(token)
                        token.startsWith("ss://") -> V2RayFmt.parseShadowsocks(token)
                        else -> null
                    }
                }.getOrNull()
            }
            .filter { it.hasValidPort() }
            .map { it.canonicalBeanOrThrow() }

    private fun extractShareLinks(line: String): List<String> {
        val starts = SHARE_LINK_START.findAll(line)
            .map { match -> match.range.first + match.value.indexOfFirst { !it.isWhitespace() } }
            .toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, start ->
            val limit = starts.getOrNull(index + 1)?.let { next ->
                line.lastIndexOf(' ', next - 1).takeIf { it >= start } ?: next
            } ?: line.length
            val end = linkTokenEnd(line, start, limit)
            line.substring(start, end).trim()
        }
    }

    private val SHARE_LINK_START = Regex("""(?:^|\s)(?:vless|vmess|trojan|ss)://""")

    private fun linkTokenEnd(line: String, start: Int, limit: Int): Int {
        var inFragment = false
        for (index in start until limit) {
            val char = line[index]
            if (char == '#') inFragment = true
            if (!inFragment && char.isWhitespace()) return index
        }
        return limit
    }

    private fun parseSingboxJson(text: String): List<AbstractBean> = runCatching {
        val root = JSONObject(text)
        val outbounds = root.optJSONArray("outbounds") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                parseOutbound(outbound)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun parseOutbound(outbound: JSONObject): AbstractBean? =
        when (outbound.optString("type")) {
            "vless" -> VLESSBean().apply {
                applyCommon(outbound)
                uuid = outbound.optString("uuid")
                flow = outbound.optString("flow")
                packetEncoding = outbound.optString("packet_encoding", packetEncoding)
                mux = outbound.optJSONObject("multiplex")?.optBoolean("enabled", false) ?: mux
                applyTransport(this, outbound)
                applyTls(this, outbound)
            }
            "vmess" -> VMessBean().apply {
                applyCommon(outbound)
                uuid = outbound.optString("uuid")
                alterId = outbound.optInt("alter_id", 0)
                encryption = outbound.optString("security", encryption)
                packetEncoding = outbound.optString("packet_encoding", packetEncoding)
                mux = outbound.optJSONObject("multiplex")?.optBoolean("enabled", false) ?: mux
                applyTransport(this, outbound)
                applyTls(this, outbound)
            }
            "trojan" -> TrojanBean().apply {
                applyCommon(outbound)
                password = outbound.optString("password")
                applyTransport(this, outbound)
                applyTls(this, outbound)
            }
            "shadowsocks" -> ShadowsocksBean().apply {
                applyCommon(outbound)
                method = outbound.optString("method", method)
                password = outbound.optString("password")
                plugin = outbound.optString("plugin")
                pluginOpts = outbound.optString("plugin_opts")
            }
            else -> null
        }?.takeIf {
            outbound.has("server") &&
                outbound.has("server_port") &&
                it.serverAddress.isNotBlank() &&
                it.hasValidPort() &&
                it.hasRequiredOutboundCredentials()
        }

    private fun AbstractBean.hasValidPort(): Boolean = serverPort in MIN_PORT..MAX_PORT

    private fun AbstractBean.applyCommon(outbound: JSONObject) {
        name = outbound.optString("tag")
        serverAddress = outbound.optString("server", serverAddress)
        serverPort = outbound.optInt("server_port", serverPort)
    }

    private fun applyTransport(bean: StandardV2RayBean, outbound: JSONObject) {
        bean.type = normalizeSingboxTransport(outbound.optString("network", bean.type))
        val transport = outbound.optJSONObject("transport") ?: return
        bean.type = normalizeSingboxTransport(transport.optString("type", bean.type))
        bean.path = transport.optString("path", bean.path)
        bean.host = transport.hostString(bean.host)
        transport.optJSONObject("headers")
            ?.hostHeader()
            ?.takeIf { it.isNotBlank() }
            ?.let { bean.host = it }
        bean.grpcServiceName = transport.optString("service_name", bean.grpcServiceName)
        bean.maxEarlyData = transport.optInt("max_early_data", bean.maxEarlyData)
        bean.earlyDataHeaderName = transport.optString("early_data_header_name", bean.earlyDataHeaderName)
    }

    private fun JSONObject.hostString(default: String): String {
        val value = opt("host") ?: return default
        if (value is org.json.JSONArray) {
            return buildList {
                for (i in 0 until value.length()) {
                    value.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString(",")
        }
        return value.toString()
    }

    private fun JSONObject.hostHeader(): String = optString("Host").ifBlank { optString("host") }

    private fun applyTls(bean: StandardV2RayBean, outbound: JSONObject) {
        val tls = outbound.optJSONObject("tls") ?: return
        if (!tls.optBoolean("enabled", false)) return
        val reality = tls.optJSONObject("reality")?.takeIf { it.optBoolean("enabled", false) }
        bean.security = if (reality != null) "reality" else "tls"
        bean.sni = tls.optString("server_name", bean.sni)
        bean.alpn = tls.optJSONArray("alpn")?.let { alpn ->
            buildList {
                for (i in 0 until alpn.length()) {
                    alpn.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString(",")
        }.orEmpty()
        bean.allowInsecure = tls.optBoolean("insecure", bean.allowInsecure)
        bean.pinnedPeerCertificatePublicKeySha256 = tls.optString(
            "certificate_public_key_sha256",
            bean.pinnedPeerCertificatePublicKeySha256,
        )
        val fingerprint = tls.optJSONObject("utls")?.optString("fingerprint").orEmpty()
        bean.utlsFingerprint = fingerprint
        if (reality != null) {
            bean.realityPublicKey = reality.optString("public_key")
            bean.realityShortId = reality.optString("short_id")
            bean.realityFingerprint = fingerprint.ifEmpty { bean.realityFingerprint }
        }
    }
}
