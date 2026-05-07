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
        appendLine("H1 = ${p.initPacketMagicHeader}")
        appendLine("H2 = ${p.responsePacketMagicHeader}")
        appendLine("H3 = ${p.cookieReplyMagicHeader}")
        appendLine("H4 = ${p.transportMagicHeader}")
    }
}
