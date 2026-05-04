package ru.ozero.enginewarp

object WarpIniBuilder {

    fun build(config: WarpConfig): String {
        val p = config.awgParams
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${config.privateKey}")
            appendLine("Address = ${config.interfaceAddressV4},${config.interfaceAddressV6}")
            appendLine("DNS = ${config.dnsServers.joinToString(", ")}")
            appendLine("MTU = ${config.mtu}")
            appendLine("Jc = ${p.junkPacketCount}")
            appendLine("Jmin = ${p.junkPacketMinSize}")
            appendLine("Jmax = ${p.junkPacketMaxSize}")
            appendLine("S1 = ${p.initPacketJunkSize}")
            appendLine("S2 = ${p.responsePacketJunkSize}")
            appendLine("H1 = ${p.initPacketMagicHeader}")
            appendLine("H2 = ${p.responsePacketMagicHeader}")
            appendLine("H3 = ${p.cookieReplyMagicHeader}")
            appendLine("H4 = ${p.transportMagicHeader}")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${config.peerPublicKey}")
            appendLine("AllowedIPs = ${config.allowedIps.joinToString(", ")}")
            appendLine("Endpoint = ${config.peerEndpoint}")
            append("PersistentKeepalive = ${config.keepaliveSeconds}")
        }
    }
}
