package ru.ozero.singboxconfig

import ru.ozero.enginescore.WireGuardOutboundConfig
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean

private const val VLESS_FLOW_XTLS_VISION = "xtls-rprx-vision"

@Suppress("TooManyFunctions")
object ConfigBuilder {

    private val SUPPORTED_TRANSPORTS = setOf("tcp", "ws", "grpc", "http", "h2", "httpupgrade", "")

    fun buildSingboxConfig(bean: AbstractBean, probeSocksPort: Int? = null): String {
        require(isSupportedBean(bean)) { "Unsupported transport: ${(bean as? StandardV2RayBean)?.type}" }
        val outbound = beanOutbound(bean, "proxy")
        return buildFullConfig(listOf(outbound), probeSocksPort)
    }

    fun buildSingboxAutoConfig(beans: List<AbstractBean>, probeSocksPort: Int? = null): String {
        val supported = beans.filter { isSupportedBean(it) }
        require(supported.isNotEmpty()) { "no beans with supported transport types" }
        val proxyOutbounds = supported.mapIndexed { index, bean -> beanOutbound(bean, "proxy-$index") }
        val tagList = proxyOutbounds.indices.joinToString(",") { jsonString("proxy-$it") }
        val urltest = buildString {
            append("""{"type":"urltest","tag":"proxy","outbounds":[$tagList],""")
            append(""""url":"https://www.gstatic.com/generate_204",""")
            append(""""interval":"3m","tolerance":50,""")
            append(""""interrupt_exist_connections":true,"idle_timeout":"30m"}""")
        }
        return buildFullConfig(listOf(urltest) + proxyOutbounds, probeSocksPort)
    }

    fun isSupportedBean(bean: AbstractBean): Boolean {
        if (bean !is StandardV2RayBean) return true
        return bean.type in SUPPORTED_TRANSPORTS
    }

    fun buildChainConfig(bean: AbstractBean, socksPort: Int, upstream: Upstream? = null): String {
        val outbound = beanOutbound(bean, "proxy", detour = upstream?.let { "upstream" })
        return buildChainFullConfig(socksPort, listOf(outbound), upstream)
    }

    fun buildAutoChainConfig(beans: List<AbstractBean>, socksPort: Int, upstream: Upstream? = null): String {
        require(beans.isNotEmpty()) { "beans must not be empty for auto-select chain config" }
        val supported = beans.filter { isSupportedBean(it) }
        require(supported.isNotEmpty()) { "no beans with supported transport types" }
        val detourTag = upstream?.let { "upstream" }
        val proxyOutbounds = supported.mapIndexed { index, bean ->
            beanOutbound(bean, "proxy-$index", detour = detourTag)
        }
        val tagList = proxyOutbounds.indices.joinToString(",") { jsonString("proxy-$it") }
        val urltest = buildString {
            append("""{"type":"urltest","tag":"proxy","outbounds":[$tagList],""")
            append(""""url":"https://www.gstatic.com/generate_204",""")
            append(""""interval":"3m","tolerance":50,""")
            append(""""interrupt_exist_connections":false,"idle_timeout":"30m"}""")
        }
        return buildChainFullConfig(socksPort, listOf(urltest) + proxyOutbounds, upstream)
    }

    fun buildWireGuardChainConfig(wg: WireGuardOutboundConfig, socksPort: Int, upstream: Upstream? = null): String {
        val outbound = wireGuardOutbound(wg, "proxy", detour = upstream?.let { "upstream" })
        return buildChainFullConfig(socksPort, listOf(outbound), upstream)
    }

    fun buildProfileChainConfig(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
        probeSocksPort: Int? = null,
    ): String {
        val outbounds = profileChainOutbounds(selected, wrappers)
        return buildFullConfig(outbounds, probeSocksPort)
    }

    fun buildProfileChainProxyConfig(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
        socksPort: Int,
    ): String {
        val outbounds = profileChainOutbounds(selected, wrappers)
        return buildChainFullConfig(socksPort, outbounds, upstream = null)
    }

    data class Upstream(val host: String, val port: Int)

    private fun profileChainOutbounds(
        selected: AbstractBean,
        wrappers: List<AbstractBean>,
    ): List<String> {
        require(isSupportedBean(selected)) { "Unsupported selected transport" }
        val supportedWrappers = wrappers.filter { isSupportedBean(it) }
        val wrapperOutbounds = supportedWrappers.mapIndexed { index, bean ->
            val detour = if (index == 0) null else "chain-${index - 1}"
            beanOutbound(bean, "chain-$index", detour)
        }
        val selectedDetour = supportedWrappers.lastIndex.takeIf { it >= 0 }?.let { "chain-$it" }
        val selectedOutbound = beanOutbound(selected, "proxy", selectedDetour)
        return wrapperOutbounds + selectedOutbound
    }

    private fun beanOutbound(bean: AbstractBean, tag: String, detour: String? = null): String = when (bean) {
        is VLESSBean -> vlessOutbound(bean, tag, detour)
        is VMessBean -> vmessOutbound(bean, tag, detour)
        is TrojanBean -> trojanOutbound(bean, tag, detour)
        is ShadowsocksBean -> shadowsocksOutbound(bean, tag, detour)
        else -> error("Unsupported bean type: ${bean::class.simpleName}")
    }

    private fun buildFullConfig(proxyOutbounds: List<String>, probeSocksPort: Int? = null): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"warn","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(tunInbound())
        if (probeSocksPort != null && probeSocksPort > 0) {
            sb.append(',')
            sb.append(socksInbound(probeSocksPort))
        }
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        proxyOutbounds.forEachIndexed { i, outbound ->
            if (i > 0) sb.append(',')
            sb.append(outbound)
        }
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append("""],""")
        sb.append(""""dns":{"servers":[{"type":"udp","tag":"dns-direct","server":"1.1.1.1"}]},""")
        sb.append(""""route":{""")
        sb.append(""""final":"proxy",""")
        sb.append(""""auto_detect_interface":true,""")
        sb.append(""""rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]""")
        sb.append('}')
        sb.append('}')
        return sb.toString()
    }

    private fun buildChainFullConfig(
        socksPort: Int,
        proxyOutbounds: List<String>,
        upstream: Upstream?,
    ): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"warn","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(socksInbound(socksPort))
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        proxyOutbounds.forEachIndexed { i, outbound ->
            if (i > 0) sb.append(',')
            sb.append(outbound)
        }
        if (upstream != null) {
            sb.append(',')
            sb.append(socksOutbound("upstream", upstream.host, upstream.port))
        }
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append("""],""")
        sb.append(""""dns":{"servers":[{"tag":"dns-remote",""")
        sb.append(""""address":"https://1.1.1.1/dns-query","detour":"proxy"}]},""")
        sb.append(""""route":{""")
        sb.append(""""final":"proxy",""")
        sb.append(""""auto_detect_interface":true,""")
        sb.append(""""rules":[{"action":"sniff"},{"protocol":"dns","action":"hijack-dns"}]""")
        sb.append('}')
        sb.append('}')
        return sb.toString()
    }

    private fun tunInbound(): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""type":"tun",""")
        sb.append(""""tag":"tun-in",""")
        sb.append(""""address":["172.19.0.1/30","fdfe:dcba:9876::1/126"],""")
        sb.append(""""mtu":9000,""")
        sb.append(""""auto_route":false,""")
        sb.append(""""strict_route":false""")
        sb.append('}')
        return sb.toString()
    }

    private fun socksInbound(port: Int): String = buildString {
        append("""{"type":"socks","tag":"socks-in",""")
        append(""""listen":"127.0.0.1","listen_port":$port}""")
    }

    private fun socksOutbound(tag: String, host: String, port: Int): String = buildString {
        append("""{"type":"socks","tag":${jsonString(tag)},""")
        append(""""server":${jsonString(host)},"server_port":$port}""")
    }

    private fun wireGuardOutbound(wg: WireGuardOutboundConfig, tag: String, detour: String? = null): String =
        buildString {
            append("""{"type":"wireguard","tag":${jsonString(tag)},""")
            append(""""server":${jsonString(wg.serverHost)},"server_port":${wg.serverPort},""")
            val addrs = wg.localAddresses.joinToString(",") { jsonString(it) }
            append(""""local_address":[$addrs],""")
            append(""""private_key":${jsonString(wg.privateKey)},""")
            append(""""peer_public_key":${jsonString(wg.peerPublicKey)},""")
            append(""""mtu":${wg.mtu}""")
            if (wg.keepaliveSeconds > 0) append(""","persistent_keepalive_interval":${wg.keepaliveSeconds}""")
            if (detour != null) append(""","detour":${jsonString(detour)}""")
            append('}')
        }
}

private fun vlessOutbound(bean: VLESSBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"vless","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""uuid":${jsonString(bean.uuid)},""")
    val flow = normalizeVlessFlow(bean.flow)
    if (flow.isNotEmpty()) {
        sb.append(""""flow":${jsonString(flow)},""")
    }

    val transport = buildTransport(bean)
    if (transport != null) {
        sb.append(""""transport":$transport,""")
    }

    val tls = buildTls(bean)
    if (tls != null) {
        sb.append(""""tls":$tls,""")
    }

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    sb.append(""""packet_encoding":"xudp"}""")
    return sb.toString()
}

private fun normalizeVlessFlow(flow: String): String = when {
    flow.isBlank() -> ""
    flow == VLESS_FLOW_XTLS_VISION -> flow
    flow.startsWith("$VLESS_FLOW_XTLS_VISION-") -> VLESS_FLOW_XTLS_VISION
    else -> ""
}

private fun vmessOutbound(bean: VMessBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"vmess","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""uuid":${jsonString(bean.uuid)},""")
    sb.append(""""alter_id":${bean.alterId},""")
    sb.append(""""security":${jsonString(bean.encryption.ifEmpty { "auto" })},""")

    val transport = buildTransport(bean)
    if (transport != null) sb.append(""""transport":$transport,""")

    val tls = buildTls(bean)
    if (tls != null) sb.append(""""tls":$tls,""")

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    sb.append(""""packet_encoding":"xudp"}""")
    return sb.toString()
}

private fun trojanOutbound(bean: TrojanBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"trojan","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""password":${jsonString(bean.password)},""")

    val transport = buildTransport(bean)
    if (transport != null) sb.append(""""transport":$transport,""")

    val tls = buildTls(bean)
    if (tls != null) sb.append(""""tls":$tls,""")

    if (detour != null) sb.append(""""detour":${jsonString(detour)},""")
    if (sb[sb.length - 1] == ',') sb.deleteCharAt(sb.length - 1)
    sb.append('}')
    return sb.toString()
}

private fun shadowsocksOutbound(bean: ShadowsocksBean, tag: String, detour: String? = null): String {
    val sb = StringBuilder()
    sb.append("""{"type":"shadowsocks","tag":${jsonString(tag)},""")
    sb.append(""""server":${jsonString(bean.serverAddress)},""")
    sb.append(""""server_port":${bean.serverPort},""")
    sb.append(""""method":${jsonString(bean.method)},""")
    sb.append(""""password":${jsonString(bean.password)}""")
    if (bean.plugin.isNotEmpty()) {
        sb.append(""","plugin":${jsonString(bean.plugin)}""")
        if (bean.pluginOpts.isNotEmpty()) {
            sb.append(""","plugin_opts":${jsonString(bean.pluginOpts)}""")
        }
    }
    if (detour != null) sb.append(""","detour":${jsonString(detour)}""")
    sb.append('}')
    return sb.toString()
}

private fun buildTransport(bean: StandardV2RayBean): String? = when (bean.type) {
    "ws" -> {
        val legacyEarlyData = bean.earlyDataHeaderName.toIntOrNull().takeIf { bean.maxEarlyData <= 0 }
        buildMap(
            "type" to "ws",
            "path" to (bean.path.ifEmpty { "/" }),
            "headers" to if (bean.host.isNotEmpty()) """{"Host":${jsonString(bean.host)}}""" else "{}",
            "max_early_data" to (bean.maxEarlyData.takeIf { it > 0 } ?: legacyEarlyData ?: 0).toString(),
            "early_data_header_name" to bean.earlyDataHeaderName.takeUnless { legacyEarlyData != null }.orEmpty(),
        )
    }
    "grpc" -> buildMap(
        "type" to "grpc",
        "service_name" to bean.grpcServiceName,
    )
    "http", "h2" -> buildMap(
        "type" to "http",
        "path" to (bean.path.ifEmpty { "/" }),
        "host" to if (bean.host.isNotEmpty()) """[${jsonString(bean.host)}]""" else "[]",
    )
    "httpupgrade" -> buildMap(
        "type" to "httpupgrade",
        "path" to (bean.path.ifEmpty { "/" }),
        "host" to bean.host,
    )
    "splithttp" -> buildMap(
        "type" to "splithttp",
        "path" to (bean.path.ifEmpty { "/" }),
        "host" to bean.host,
    )
    "tcp" -> null
    else -> null
}

private fun buildTls(bean: StandardV2RayBean): String? {
    val security = bean.security
    if (security == "none" || security.isEmpty()) return null

    val sb = StringBuilder()
    sb.append("""{"enabled":true,""")
    sb.append(""""server_name":${jsonString(tlsServerName(bean))},""")

    if (bean.alpn.isNotEmpty()) {
        val alpns = bean.alpn.split(",").joinToString(",") { jsonString(it.trim()) }
        sb.append(""""alpn":[$alpns],""")
    }

    if (security == "reality") {
        sb.append(""""reality":{"enabled":true,""")
        sb.append(""""public_key":${jsonString(bean.realityPublicKey)},""")
        sb.append(""""short_id":${jsonString(bean.realityShortId)}},""")
        val fp = bean.realityFingerprint.ifEmpty { "chrome" }
        sb.append(""""utls":{"enabled":true,"fingerprint":${jsonString(fp)}},""")
    } else if (security == "tls") {
        if (bean.utlsFingerprint.isNotEmpty()) {
            sb.append(""""utls":{"enabled":true,"fingerprint":${jsonString(bean.utlsFingerprint)}},""")
        }
        if (bean.allowInsecure) {
            sb.append(""""insecure":true,""")
        }
        if (bean.certificates.isNotEmpty()) {
            sb.append(""""certificate":${jsonString(bean.certificates)},""")
        }
    }

    sb.append(""""disable_sni":false}""")
    return sb.toString()
}

private fun tlsServerName(bean: StandardV2RayBean): String {
    if (bean.sni.isNotEmpty()) return bean.sni
    val host = bean.host.trim()
    return if (host.isNotEmpty() && "," !in host && ";" !in host) host else bean.serverAddress
}

private fun buildMap(vararg pairs: Pair<String, String>): String {
    val fields = pairs.filter { (_, v) -> v.isNotEmpty() && v != "0" && v != "[]" && v != "{}" }
        .joinToString(",") { (k, v) ->
            val isLiteral = k == "max_early_data" ||
                v.startsWith("{") ||
                v.startsWith("[") ||
                v == "true" ||
                v == "false"
            val value = if (isLiteral) {
                v
            } else {
                jsonString(v)
            }
            "${jsonString(k)}:$value"
        }
    return "{$fields}"
}

private fun jsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '' -> sb.append("\\f")
            else -> if (c.code < 0x20) {
                sb.append("\\u%04x".format(c.code))
            } else {
                sb.append(c)
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
