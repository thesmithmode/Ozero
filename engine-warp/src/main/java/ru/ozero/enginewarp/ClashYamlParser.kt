package ru.ozero.enginewarp

object ClashYamlParser {

    class ParseException(message: String) : Exception(message)

    data class ClashParseResult(
        val endpoints: List<String>,
        val privateKey: String,
        val peerPublicKey: String,
        val interfaceAddressV4: String,
        val interfaceAddressV6: String,
        val dnsServers: List<String>,
        val mtu: Int,
        val keepaliveSeconds: Int,
        val awgParams: AwgParams,
    )

    fun parse(yaml: String): ClashParseResult {
        if (yaml.isBlank()) throw ParseException("YAML пустой")
        val lines = yaml.lines()
        val anchors = collectAnchors(lines)
        val commonData = anchors.values.firstOrNull { "private-key" in it }
            ?: throw ParseException("Anchor с private-key не найден")
        val endpoints = collectEndpoints(lines)
        if (endpoints.isEmpty()) throw ParseException("Нет эндпоинтов в proxies:")
        val awgBlock = commonData["_awg"] ?: throw ParseException("amnezia-wg-option не найден")
        return ClashParseResult(
            endpoints = endpoints,
            privateKey = commonData["private-key"] ?: throw ParseException("private-key отсутствует"),
            peerPublicKey = commonData["public-key"] ?: throw ParseException("public-key отсутствует"),
            interfaceAddressV4 = commonData["ip"]?.withCidr("/32") ?: "",
            interfaceAddressV6 = commonData["ipv6"]?.withCidr("/128") ?: "",
            dnsServers = commonData["dns"]?.let(::parseDnsList) ?: WarpConfig.DEFAULT_DNS,
            mtu = commonData["mtu"]?.toIntOrNull() ?: 1280,
            keepaliveSeconds = 25,
            awgParams = parseAwgBlock(awgBlock),
        )
    }

    fun slotNameFromFilename(filename: String): String {
        val digits = Regex("""\d+""").find(filename.substringBeforeLast("."))?.value
        if (!digits.isNullOrEmpty()) return "Ozero-Ultra-$digits"
        return "Ozero-Ultra-${(Math.abs(filename.hashCode()) % 100000).toString().padStart(5, '0')}"
    }

    private fun collectAnchors(lines: List<String>): Map<String, Map<String, String>> {
        val anchors = mutableMapOf<String, Map<String, String>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith('#')) { i++; continue }
            val trimmed = line.trimStart()
            val lineIndent = line.length - trimmed.length
            if (lineIndent == 0) {
                val m = Regex("""^[\w-]+:\s*&([\w-]+)\s*$""").find(trimmed)
                if (m != null) {
                    val anchorName = m.groupValues[1]
                    val data = mutableMapOf<String, String>()
                    i++
                    val awgLines = mutableListOf<String>()
                    var inAwg = false
                    while (i < lines.size) {
                        val inner = lines[i]
                        if (inner.isBlank() || inner.trimStart().startsWith('#')) { i++; continue }
                        val innerTrimmed = inner.trimStart()
                        val innerIndent = inner.length - innerTrimmed.length
                        if (innerIndent == 0) break
                        when {
                            innerTrimmed.startsWith("amnezia-wg-option:") -> { inAwg = true; i++ }
                            inAwg -> { awgLines.add(innerTrimmed); i++ }
                            else -> {
                                inAwg = false
                                val colon = innerTrimmed.indexOf(':')
                                if (colon > 0) {
                                    val key = innerTrimmed.substring(0, colon).trim()
                                    val value = innerTrimmed.substring(colon + 1).trim().unquote()
                                    if (value.isNotEmpty()) data[key] = value
                                }
                                i++
                            }
                        }
                    }
                    if (awgLines.isNotEmpty()) data["_awg"] = awgLines.joinToString("\n")
                    anchors[anchorName] = data
                    continue
                }
            }
            i++
        }
        return anchors
    }

    private fun collectEndpoints(lines: List<String>): List<String> {
        val endpoints = mutableListOf<String>()
        val proxiesIdx = lines.indexOfFirst { it.trimStart() == "proxies:" }
        if (proxiesIdx < 0) return endpoints
        var i = proxiesIdx + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.trimStart().startsWith('#')) { i++; continue }
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            if (indent == 0 && !trimmed.startsWith('-')) break
            if (trimmed.startsWith("- ")) {
                var server: String? = null
                var port: String? = null
                i++
                inner@ while (i < lines.size) {
                    val inner = lines[i]
                    if (inner.isBlank() || inner.trimStart().startsWith('#')) { i++; continue@inner }
                    val innerTrimmed = inner.trimStart()
                    val innerIndent = inner.length - innerTrimmed.length
                    if (innerIndent <= indent) break@inner
                    val colon = innerTrimmed.indexOf(':')
                    if (colon > 0) {
                        val key = innerTrimmed.substring(0, colon).trim()
                        val value = innerTrimmed.substring(colon + 1).trim().unquote()
                        when (key) {
                            "server" -> server = value
                            "port" -> port = value
                        }
                    }
                    i++
                }
                if (server != null && port != null) endpoints.add("$server:$port")
            } else {
                i++
            }
        }
        return endpoints
    }

    private fun parseAwgBlock(block: String): AwgParams {
        val map = mutableMapOf<String, String>()
        block.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val colon = line.indexOf(':')
            if (colon < 0) return@forEach
            map[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
        }
        val hexRe = Regex("""<b\s+0x([0-9a-fA-F]+)>""")
        fun hex(key: String) = map[key]?.let { hexRe.find(it)?.groupValues?.get(1)?.lowercase() }
        fun intV(key: String) = map[key]?.toIntOrNull() ?: 0
        fun longV(key: String, default: Long) = map[key]?.toLongOrNull() ?: default
        return AwgParams(
            junkPacketCount = intV("jc"),
            junkPacketMinSize = intV("jmin"),
            junkPacketMaxSize = intV("jmax"),
            initPacketJunkSize = intV("s1"),
            responsePacketJunkSize = intV("s2"),
            initPacketMagicHeader = longV("h1", 1L),
            responsePacketMagicHeader = longV("h2", 2L),
            cookieReplyMagicHeader = longV("h3", 3L),
            transportMagicHeader = longV("h4", 4L),
            payloadHexI1 = hex("i1"),
            payloadHexI2 = hex("i2"),
            payloadHexI3 = hex("i3"),
            payloadHexI4 = hex("i4"),
            payloadHexI5 = hex("i5"),
        )
    }

    private fun parseDnsList(raw: String): List<String> =
        raw.trim().removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().unquote() }
            .filter { it.isNotBlank() }

    private fun String.withCidr(cidr: String) = if (contains('/')) this else "$this$cidr"

    private fun String.unquote(): String {
        if (length >= 2 && ((startsWith('"') && endsWith('"')) || (startsWith('\'') && endsWith('\'')))) {
            return substring(1, length - 1)
        }
        return this
    }
}
