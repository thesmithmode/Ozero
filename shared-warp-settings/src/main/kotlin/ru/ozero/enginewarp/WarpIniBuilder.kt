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

    fun build(config: WarpConfig, preserveRawIni: String): String =
        RawWarpIniMerger(preserveRawIni).mergeWith(build(config))

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
        appendPayload("I1", p.payloadHexI1, p.payloadPacketSizeCount1)
        appendPayload("I2", p.payloadHexI2, p.payloadPacketSizeCount2)
        appendPayload("I3", p.payloadHexI3, p.specialJunk3)
        appendPayload("I4", p.payloadHexI4, p.specialJunk4)
        appendPayload("I5", p.payloadHexI5, p.payloadPacketSizeCount3)
    }

    private fun StringBuilder.appendPayload(label: String, hex: String?, intValue: Int) {
        when {
            hex != null -> appendLine("$label = <b 0x$hex>")
            intValue != 0 -> appendLine("$label = $intValue")
        }
    }
}

private class RawWarpIniMerger(preserveRawIni: String) {
    private val preserved = parseSections(preserveRawIni)

    fun mergeWith(generatedIni: String): String {
        if (preserved.isEmpty()) return generatedIni
        val generated = parseSections(generatedIni)
        val rebuilt = linkedMapOf<String, Section>().apply {
            putAll(preserved)
            put("interface", mergeSection("interface", generated))
            put("peer", mergeSection("peer", generated))
        }
        return renderSections(rebuilt, "interface", "peer")
    }

    private fun mergeSection(name: String, generated: Map<String, Section>): Section {
        return Section(
            header = preserved[name]?.header ?: defaultHeader(name),
            lines = mergeSectionBody(
                generated[name]?.lines.orEmpty(),
                preserved[name]?.lines.orEmpty(),
            ),
        )
    }

    private fun defaultHeader(name: String): String = when (name) {
        "interface" -> "[Interface]"
        "peer" -> "[Peer]"
        else -> "[$name]"
    }

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
            val sectionName = current ?: return@forEach
            sections.getValue(sectionName).lines.add(line)
        }
        return sections
    }

    private fun renderSections(
        sections: Map<String, Section>,
        preferredFirst: String,
        preferredSecond: String,
    ): String {
        val output = StringBuilder()
        val rendered = mutableSetOf<String>()
        appendPreferredSection(output, sections, rendered, preferredFirst)
        appendPreferredSection(output, sections, rendered, preferredSecond)

        val remaining = LinkedHashSet(sections.keys)
        remaining.removeAll(rendered)
        for (sectionName in remaining) {
            appendSection(output, sections.getValue(sectionName))
        }
        return output.toString().trimEnd()
    }

    private fun appendPreferredSection(
        output: StringBuilder,
        sections: Map<String, Section>,
        rendered: MutableSet<String>,
        name: String,
    ) {
        val section = sections[name] ?: return
        if (rendered.add(name)) appendSection(output, section)
    }

    private fun appendSection(output: StringBuilder, section: Section) {
        if (output.isNotEmpty()) output.appendLine()
        output.appendLine(section.header)
        section.lines.forEach { output.appendLine(it) }
    }

    private fun mergeSectionBody(
        generated: List<String>,
        existing: List<String>,
    ): MutableList<String> {
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

    private fun canonicalLabel(key: String): String = canonicalLabels[key.lowercase()] ?: key

    private data class Section(
        val header: String,
        val lines: MutableList<String>,
    )

    private companion object {
        val canonicalLabels = mapOf(
            "address" to "Address",
            "persistentkeepalive" to "PersistentKeepalive",
            "allowedips" to "AllowedIPs",
            "privatekey" to "PrivateKey",
            "publickey" to "PublicKey",
            "endpoint" to "Endpoint",
            "dns" to "DNS",
            "mtu" to "MTU",
            "jc" to "Jc",
            "jmin" to "Jmin",
            "jmax" to "Jmax",
            "s1" to "S1",
            "s2" to "S2",
            "s3" to "S3",
            "s4" to "S4",
            "h1" to "H1",
            "h2" to "H2",
            "h3" to "H3",
            "h4" to "H4",
            "i1" to "I1",
            "i2" to "I2",
            "i3" to "I3",
            "i4" to "I4",
            "i5" to "I5",
        )
    }
}
