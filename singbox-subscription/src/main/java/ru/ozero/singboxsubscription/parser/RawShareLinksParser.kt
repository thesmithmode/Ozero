package ru.ozero.singboxsubscription.parser

import org.json.JSONObject
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.V2RayFmt
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean

object RawShareLinksParser {
    fun parse(text: String): List<AbstractBean> {
        val links = parseShareLinks(text)
        return links.ifEmpty { parseSingboxJson(text) }
    }

    private fun parseShareLinks(text: String): List<AbstractBean> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { it.split(" ") }
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
                applyTransport(this, outbound)
                applyTls(this, outbound)
            }
            "vmess" -> VMessBean().apply {
                applyCommon(outbound)
                uuid = outbound.optString("uuid")
                alterId = outbound.optInt("alter_id", 0)
                encryption = outbound.optString("security", encryption)
                packetEncoding = outbound.optString("packet_encoding", packetEncoding)
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
        }?.takeIf { it.serverAddress.isNotBlank() && it.serverPort > 0 }

    private fun AbstractBean.applyCommon(outbound: JSONObject) {
        name = outbound.optString("tag")
        serverAddress = outbound.optString("server", serverAddress)
        serverPort = outbound.optInt("server_port", serverPort)
    }

    private fun applyTransport(bean: StandardV2RayBean, outbound: JSONObject) {
        val transport = outbound.optJSONObject("transport") ?: return
        bean.type = when (val type = transport.optString("type", bean.type)) {
            "xhttp" -> "splithttp"
            else -> type
        }
        bean.path = transport.optString("path", bean.path)
        bean.host = transport.optString("host", bean.host)
        transport.optJSONObject("headers")
            ?.optString("Host")
            ?.takeIf { it.isNotBlank() }
            ?.let { bean.host = it }
        bean.grpcServiceName = transport.optString("service_name", bean.grpcServiceName)
    }

    private fun applyTls(bean: StandardV2RayBean, outbound: JSONObject) {
        val tls = outbound.optJSONObject("tls") ?: return
        if (!tls.optBoolean("enabled", false)) return
        val reality = tls.optJSONObject("reality")
        val realityEnabled = reality?.optBoolean("enabled", false) == true
        bean.security = if (realityEnabled) "reality" else "tls"
        bean.sni = tls.optString("server_name", bean.sni)
        bean.alpn = tls.optJSONArray("alpn")?.let { alpn ->
            buildList {
                for (i in 0 until alpn.length()) {
                    alpn.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString(",")
        }.orEmpty()
        bean.allowInsecure = tls.optBoolean("insecure", bean.allowInsecure)
        val fingerprint = tls.optJSONObject("utls")?.optString("fingerprint").orEmpty()
        bean.utlsFingerprint = fingerprint
        if (realityEnabled && reality != null) {
            bean.realityPublicKey = reality.optString("public_key")
            bean.realityShortId = reality.optString("short_id")
            bean.realityFingerprint = fingerprint.ifEmpty { bean.realityFingerprint }
        }
    }
}
