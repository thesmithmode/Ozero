package ru.ozero.singboxconfig

import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean

object ConfigBuilder {

    fun buildSingboxConfig(bean: AbstractBean): String {
        val outbound = beanOutbound(bean, "proxy")
        return buildFullConfig(listOf(outbound))
    }

    fun buildSingboxAutoConfig(beans: List<AbstractBean>): String {
        require(beans.isNotEmpty()) { "beans must not be empty for auto-select config" }
        val proxyOutbounds = beans.mapIndexed { index, bean ->
            beanOutbound(bean, "proxy-$index")
        }
        val tags = proxyOutbounds.indices.map { "proxy-$it" }
        val urltestOutbound = buildUrltestOutbound(tags)
        return buildFullConfig(listOf(urltestOutbound) + proxyOutbounds)
    }

    private fun beanOutbound(bean: AbstractBean, tag: String): String = when (bean) {
        is VLESSBean -> vlessOutbound(bean, tag)
        is VMessBean -> vmessOutbound(bean, tag)
        is TrojanBean -> trojanOutbound(bean, tag)
        is ShadowsocksBean -> shadowsocksOutbound(bean, tag)
        else -> error("Unsupported bean type: ${bean::class.simpleName}")
    }

    private fun buildUrltestOutbound(tags: List<String>): String {
        val sb = StringBuilder()
        sb.append("""{"type":"urltest","tag":"proxy",""")
        sb.append(""""outbounds":[${tags.joinToString(",") { jsonString(it) }}],""")
        sb.append(""""url":"https://www.gstatic.com/generate_204",""")
        sb.append(""""interval":"3m",""")
        sb.append(""""tolerance":50,""")
        sb.append(""""interrupt_exist_connections":false,""")
        sb.append(""""idle_timeout":"30m"}""")
        return sb.toString()
    }

    private fun buildFullConfig(proxyOutbounds: List<String>): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""log":{"level":"warn","timestamp":true},""")
        sb.append(""""inbounds":[""")
        sb.append(tunInbound())
        sb.append("""],""")
        sb.append(""""outbounds":[""")
        proxyOutbounds.forEachIndexed { i, outbound ->
            if (i > 0) sb.append(',')
            sb.append(outbound)
        }
        sb.append(""",{"type":"direct","tag":"direct"}""")
        sb.append(""",{"type":"block","tag":"block"}""")
        sb.append(""",{"type":"dns","tag":"dns-out"}""")
        sb.append("""],""")
        sb.append(""""dns":{"servers":[{"tag":"dns-direct","address":"1.1.1.1"}]},""")
        sb.append(""""route":{""")
        sb.append(""""final":"proxy",""")
        sb.append(""""auto_detect_interface":true,""")
        sb.append(""""rules":[{"protocol":"dns","outbound":"dns-out"}]""")
        sb.append('}')
        sb.append('}')
        return sb.toString()
    }

    private fun tunInbound(): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append(""""type":"tun",""")
        sb.append(""""tag":"tun-in",""")
        sb.append(""""inet4_address":"172.19.0.1/30",""")
        sb.append(""""inet6_address":"fdfe:dcba:9876::1/126",""")
        sb.append(""""mtu":9000,""")
        sb.append(""""auto_route":true,""")
        sb.append(""""strict_route":false,""")
        sb.append(""""sniff":true,""")
        sb.append(""""sniff_override_destination":true""")
        sb.append('}')
        return sb.toString()
    }

    private fun vlessOutbound(bean: VLESSBean, tag: String): String {
        val sb = StringBuilder()
        sb.append("""{"type":"vless","tag":${jsonString(tag)},""")
        sb.append(""""server":${jsonString(bean.serverAddress)},""")
        sb.append(""""server_port":${bean.serverPort},""")
        sb.append(""""uuid":${jsonString(bean.uuid)},""")
        if (bean.flow.isNotEmpty()) {
            sb.append(""""flow":${jsonString(bean.flow)},""")
        }

        val transport = buildTransport(bean)
        if (transport != null) {
            sb.append(""""transport":$transport,""")
        }

        val tls = buildTls(bean)
        if (tls != null) {
            sb.append(""""tls":$tls,""")
        }

        sb.append(""""packet_encoding":"xudp"}""")
        return sb.toString()
    }

    private fun vmessOutbound(bean: VMessBean, tag: String): String {
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

        sb.append(""""packet_encoding":"xudp"}""")
        return sb.toString()
    }

    private fun trojanOutbound(bean: TrojanBean, tag: String): String {
        val sb = StringBuilder()
        sb.append("""{"type":"trojan","tag":${jsonString(tag)},""")
        sb.append(""""server":${jsonString(bean.serverAddress)},""")
        sb.append(""""server_port":${bean.serverPort},""")
        sb.append(""""password":${jsonString(bean.password)},""")

        val transport = buildTransport(bean)
        if (transport != null) sb.append(""""transport":$transport,""")

        val tls = buildTls(bean)
        if (tls != null) sb.append(""""tls":$tls,""")

        removeTrailingComma(sb)
        sb.append('}')
        return sb.toString()
    }

    private fun shadowsocksOutbound(bean: ShadowsocksBean, tag: String): String {
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
        sb.append('}')
        return sb.toString()
    }

    private fun removeTrailingComma(sb: StringBuilder) {
        if (sb.isNotEmpty() && sb[sb.length - 1] == ',') {
            sb.deleteCharAt(sb.length - 1)
        }
    }

    private fun buildTransport(bean: StandardV2RayBean): String? = when (bean.type) {
        "ws" -> buildMap(
            "type" to "ws",
            "path" to (bean.path.ifEmpty { "/" }),
            "headers" to if (bean.host.isNotEmpty()) """{"Host":${jsonString(bean.host)}}""" else "{}",
            "max_early_data" to bean.maxEarlyData.toString(),
            "early_data_header_name" to bean.earlyDataHeaderName,
        )
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
        sb.append(""""server_name":${jsonString(bean.sni.ifEmpty { bean.serverAddress })},""")

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

    private fun buildMap(vararg pairs: Pair<String, String>): String {
        val fields = pairs.filter { (_, v) -> v.isNotEmpty() && v != "0" && v != "[]" && v != "{}" }
            .joinToString(",") { (k, v) ->
                val isLiteral = v.startsWith("{") ||
                    v.startsWith("[") ||
                    v == "true" ||
                    v == "false" ||
                    v.all { c -> c.isDigit() }
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
}
