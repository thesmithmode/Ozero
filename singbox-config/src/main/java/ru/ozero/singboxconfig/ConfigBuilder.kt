package ru.ozero.singboxconfig

import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.VLESSBean

object ConfigBuilder {

    fun buildSingboxConfig(bean: AbstractBean): String {
        return when (bean) {
            is VLESSBean -> buildVless(bean)
            else -> error("Unsupported bean type in P1: ${bean::class.simpleName}")
        }
    }

    private fun buildVless(bean: VLESSBean): String {
        val sb = StringBuilder()
        sb.append("""{"log":{"level":"warn","timestamp":true},"outbounds":[""")
        sb.append(vlessOutbound(bean))
        sb.append(""",{"type":"direct","tag":"direct"},{"type":"block","tag":"block"}]""")
        sb.append(""","route":{"final":"proxy","auto_detect_interface":true,"rules":[]}}""")
        return sb.toString()
    }

    private fun vlessOutbound(bean: VLESSBean): String {
        val sb = StringBuilder()
        sb.append("""{"type":"vless","tag":"proxy",""")
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
