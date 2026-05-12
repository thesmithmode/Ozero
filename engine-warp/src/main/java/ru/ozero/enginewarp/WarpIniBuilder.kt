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
        appendLine("S3 = ${p.underloadPacketJunkSize}")
        appendLine("S4 = ${p.payloadPacketJunkSize}")
        appendLine("H1 = ${p.initPacketMagicHeader}")
        appendLine("H2 = ${p.responsePacketMagicHeader}")
        appendLine("H3 = ${p.cookieReplyMagicHeader}")
        appendLine("H4 = ${p.transportMagicHeader}")
        appendLine("I1 = ${p.payloadPacketSizeCount1}")
        appendLine("I2 = ${p.payloadPacketSizeCount2}")
        if (p.specialJunk3 != 0) appendLine("I3 = ${p.specialJunk3}")
        if (p.specialJunk4 != 0) appendLine("I4 = ${p.specialJunk4}")
        appendLine("I5 = ${p.payloadPacketSizeCount3}")
    }
}
