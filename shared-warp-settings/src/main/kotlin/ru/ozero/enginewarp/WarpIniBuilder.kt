package ru.ozero.enginewarp

object WarpIniBuilder {

    fun build(config: WarpConfig): String {
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${config.privateKey}")
            val addresses = listOfNotNull(
                config.interfaceAddressV4.takeIf { it.isNotBlank() },
                config.interfaceAddressV6.takeIf { it.isNotBlank() },
            ).joinToString(", ")
            appendLine("Address = $addresses")
            appendLine("DNS = ${config.dnsServers.joinToString(", ")}")
            appendLine("MTU = ${config.mtu}")
            appendAwgIfPresent(config.awgParams)
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${config.peerPublicKey}")
            appendLine("AllowedIPs = ${config.allowedIps.joinToString(", ")}")
            appendLine("Endpoint = ${config.peerEndpoint}")
            append("PersistentKeepalive = ${config.keepaliveSeconds}")
        }
    }

    fun build(config: WarpConfig, preserveRawIni: String): String {
        val preserved = parseSections(preserveRawIni)
        if (preserved.isEmpty()) {
            return build(config)
        }
        val baseIni = build(config)
        val baseSections = parseSections(baseIni)
        val rebuiltSections = linkedMapOf<String, Section>().apply {
            putAll(preserved)
            val mergedInterface = mergeSectionBody(
                baseSections["interface"]?.lines.orEmpty(),
                preserved["interface"]?.lines.orEmpty(),
            )
            val mergedPeer = mergeSectionBody(
                baseSections["peer"]?.lines.orEmpty(),
                preserved["peer"]?.lines.orEmpty(),
            )
            val interfaceHeader = preserved["interface"]?.header ?: "[Interface]"
            val peerHeader = preserved["peer"]?.header ?: "[Peer]"
            put(
                "interface",
                Section(
                    header = interfaceHeader,
                    lines = mergedInterface,
                ),
            )
            put(
                "peer",
                Section(
                    header = peerHeader,
                    lines = mergedPeer,
                ),
            )
        }

        return renderSections(rebuiltSections, "interface", "peer")
    }

    private fun renderSections(
        sections: Map<String, Section>,
        preferredFirst: String,
        preferredSecond: String,
    ): String {
        val output = StringBuilder()
        val rendered = mutableSetOf<String>()
        if (sections.containsKey(preferredFirst)) {
            appendSection(output, sections[preferredFirst]!!)
            rendered.add(preferredFirst)
        }
        if (sections.containsKey(preferredSecond) && rendered.add(preferredSecond)) {
            if (output.isNotEmpty()) output.appendLine()
            appendSection(output, sections[preferredSecond]!!)
        }

        val remaining = LinkedHashSet(sections.keys)
        remaining.removeAll(rendered)
        for (sectionName in remaining) {
            if (output.isNotEmpty()) output.appendLine()
            appendSection(output, sections[sectionName]!!)
        }
        return output.toString().trimEnd()
    }

    private fun appendSection(output: StringBuilder, section: Section) {
        output.appendLine(section.header)
        section.lines.forEach { output.appendLine(it) }
    }

    private fun StringBuilder.appendAwgIfPresent(p: AwgParams) {
        if (p == AwgParams.VANILLA) return
        appendLine("Jc = ${p.junkPacketCount}")
        appendLine("Jmin = ${p.junkPacketMinSize}")
        appendLine("Jmax = ${p.junkPacketMaxSize}")
        appendLine("S1 = ${p.initPacketJunkSize}")
        appendLine("S2 = ${p.responsePacketJunkSize}")
        if (p.underloadPacketJunkSize != 0) appendLine("S3 = ${p.underloadPacketJunkSize}")
        if (p.payloadPacketJunkSize != 0) appendLine("S4 = ${p.payloadPacketJunkSize}")
        appendLine("H1 = ${p.initPacketMagicHeader}")
        appendLine("H2 = ${p.responsePacketMagicHeader}")
        appendLine("H3 = ${p.cookieReplyMagicHeader}")
        appendLine("H4 = ${p.transportMagicHeader}")
        when {
            p.payloadHexI1 != null -> appendLine("I1 = ${formatI(p.payloadHexI1, p.payloadPacketSizeCount1)}")
            p.payloadPacketSizeCount1 != 0 -> appendLine("I1 = ${p.payloadPacketSizeCount1}")
        }
        when {
            p.payloadHexI2 != null -> appendLine("I2 = ${formatI(p.payloadHexI2, p.payloadPacketSizeCount2)}")
            p.payloadPacketSizeCount2 != 0 -> appendLine("I2 = ${p.payloadPacketSizeCount2}")
        }
        when {
            p.payloadHexI3 != null -> appendLine("I3 = <b 0x${p.payloadHexI3}>")
            p.specialJunk3 != 0 -> appendLine("I3 = ${p.specialJunk3}")
        }
        when {
            p.payloadHexI4 != null -> appendLine("I4 = <b 0x${p.payloadHexI4}>")
            p.specialJunk4 != 0 -> appendLine("I4 = ${p.specialJunk4}")
        }
        when {
            p.payloadHexI5 != null -> appendLine("I5 = ${formatI(p.payloadHexI5, p.payloadPacketSizeCount3)}")
            p.payloadPacketSizeCount3 != 0 -> appendLine("I5 = ${p.payloadPacketSizeCount3}")
        }
    }

    private fun formatI(hex: String?, intValue: Int): String =
        if (hex != null) "<b 0x$hex>" else intValue.toString()

    private fun parseSections(rawIni: String): Map<String, Section> {
        val sections = LinkedHashMap<String, Section>()
        val header = Regex("^\\s*\\[(.+)]\\s*$")
        var current: String? = null
        rawIni.lines().forEach { line ->
            val headerName = header.matchEntire(line.trim())?.groupValues?.getOrNull(1)?.trim()?.lowercase()
            if (headerName != null) {
                current = headerName
                sections.putIfAbsent(headerName, Section(line.trim(), mutableListOf()))
                return@forEach
            }
            if (current == null) return@forEach
            sections[current]!!.lines.add(line)
        }
        return sections
    }

    private fun mergeSectionBody(
        generated: List<String>,
        existing: List<String>,
    ): List<String> {
        val generatedByKey = generated
            .mapNotNull { parseKeyValue(it) }
            .associateBy({ it.first }, { it.second })
        val seen = HashSet<String>()
        val result = mutableListOf<String>()

        existing.forEach { line ->
            val key = parseKeyValue(line)?.first
            if (key == null || generatedByKey[key] == null) {
                result.add(line)
            } else if (seen.add(key)) {
                result.add("${canonicalLabel(key)} = ${generatedByKey[key]}")
            }
        }

        generated.forEach { line ->
            val kv = parseKeyValue(line) ?: return@forEach
            if (kv.first !in seen) {
                result.add("${canonicalLabel(kv.first)} = ${kv.second}")
            }
            seen.add(kv.first)
        }
        return result
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val eq = line.indexOf('=')
        if (eq <= 0) return null
        return line.substring(0, eq).trim().lowercase() to line.substring(eq + 1).trim()
    }

    private fun canonicalLabel(key: String): String = when (key.lowercase()) {
        "address" -> "Address"
        "persistentkeepalive" -> "PersistentKeepalive"
        "allowedips" -> "AllowedIPs"
        "privatekey" -> "PrivateKey"
        "publickey" -> "PublicKey"
        "endpoint" -> "Endpoint"
        "dns" -> "DNS"
        "mtu" -> "MTU"
        "jc" -> "Jc"
        "jmin" -> "Jmin"
        "jmax" -> "Jmax"
        "s1" -> "S1"
        "s2" -> "S2"
        "s3" -> "S3"
        "s4" -> "S4"
        "h1" -> "H1"
        "h2" -> "H2"
        "h3" -> "H3"
        "h4" -> "H4"
        "i1" -> "I1"
        "i2" -> "I2"
        "i3" -> "I3"
        "i4" -> "I4"
        "i5" -> "I5"
        else -> key
    }

    private data class Section(
        val header: String,
        val lines: List<String>,
    )
}
