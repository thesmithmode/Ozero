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
}
