package ru.ozero.enginewarp

import java.io.IOException

internal object WarpConfParser {

    fun parse(conf: String): Result<WarpConfig> = try {
        val iface = mutableMapOf<String, String>()
        val peer = mutableMapOf<String, String>()
        var section: String? = null
        conf.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            when {
                line.equals("[Interface]", ignoreCase = true) -> section = "iface"
                line.equals("[Peer]", ignoreCase = true) -> section = "peer"
                else -> applyKeyValue(line, section, iface, peer)
            }
        }
        val priv = iface["privatekey"]
            ?: throw IOException("WireGuard conf: PrivateKey отсутствует")
        val addresses = iface["address"]
            ?: throw IOException("WireGuard conf: Address отсутствует")
        val (v4, v6) = splitAddresses(addresses)
        val peerPub = peer["publickey"]
            ?: throw IOException("WireGuard conf: PublicKey peer отсутствует")
        val endpoint = peer["endpoint"]
            ?: throw IOException("WireGuard conf: Endpoint peer отсутствует")
        val mtu = iface["mtu"]?.toIntOrNull() ?: WarpConfig.DEFAULT_MTU
        val dnsServers = parseDnsServers(iface["dns"])
        val keepalive = peer["persistentkeepalive"]?.toIntOrNull() ?: WarpConfig.DEFAULT_KEEPALIVE
        val awgParams = parseAwgParams(iface)
        Result.success(
            WarpConfig(
                privateKey = priv,
                publicKey = "",
                peerPublicKey = peerPub,
                peerEndpoint = endpoint,
                interfaceAddressV4 = v4,
                interfaceAddressV6 = v6,
                accountLicense = "",
                mtu = mtu,
                dnsServers = dnsServers,
                keepaliveSeconds = keepalive,
                awgParams = awgParams,
            ),
        )
    } catch (t: Throwable) {
        Result.failure(IOException("WireGuard conf parse: ${t.message}", t))
    }

    private fun applyKeyValue(
        line: String,
        section: String?,
        iface: MutableMap<String, String>,
        peer: MutableMap<String, String>,
    ) {
        val kv = parseKeyValue(line) ?: return
        when (section) {
            "iface" -> iface[kv.first] = kv.second
            "peer" -> peer[kv.first] = kv.second
        }
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val eq = line.indexOf('=')
        if (eq <= 0) return null
        return line.substring(0, eq).trim().lowercase() to line.substring(eq + 1).trim()
    }

    private fun splitAddresses(addresses: String): Pair<String, String> {
        val parts = addresses.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val v4 = parts.firstOrNull { ":" !in it } ?: ""
        val v6 = parts.firstOrNull { ":" in it } ?: ""
        return toCidr(v4, "/32") to toCidr(v6, "/128")
    }

    private fun toCidr(addr: String, suffix: String): String = when {
        addr.contains("/") -> addr
        addr.isNotBlank() -> "$addr$suffix"
        else -> addr
    }

    private fun parseDnsServers(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return WarpConfig.DEFAULT_DNS
        val servers = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return servers.ifEmpty { WarpConfig.DEFAULT_DNS }
    }

    private fun parseAwgParams(iface: Map<String, String>): AwgParams = AwgParams(
        junkPacketCount = iface["jc"]?.toIntOrNull() ?: 0,
        junkPacketMinSize = iface["jmin"]?.toIntOrNull() ?: 0,
        junkPacketMaxSize = iface["jmax"]?.toIntOrNull() ?: 0,
        initPacketJunkSize = iface["s1"]?.toIntOrNull() ?: AwgParams.DEFAULT_S1,
        responsePacketJunkSize = iface["s2"]?.toIntOrNull() ?: AwgParams.DEFAULT_S2,
        initPacketMagicHeader = iface["h1"]?.toLongOrNull() ?: AwgParams.DEFAULT_H1,
        responsePacketMagicHeader = iface["h2"]?.toLongOrNull() ?: AwgParams.DEFAULT_H2,
        cookieReplyMagicHeader = iface["h3"]?.toLongOrNull() ?: AwgParams.DEFAULT_H3,
        transportMagicHeader = iface["h4"]?.toLongOrNull() ?: AwgParams.DEFAULT_H4,
    )
}
