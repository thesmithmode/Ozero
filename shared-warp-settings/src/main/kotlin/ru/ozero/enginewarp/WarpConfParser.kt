package ru.ozero.enginewarp

import java.io.IOException

object WarpConfParser {

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
            ?: throw IOException("WireGuard conf: PrivateKey is missing")
        val addresses = iface["address"]
            ?: throw IOException("WireGuard conf: Address is missing")
        val (v4, v6) = splitAddresses(addresses)
        val peerPub = peer["publickey"]
            ?: throw IOException("WireGuard conf: peer PublicKey is missing")
        val endpoint = peer["endpoint"]
            ?: throw IOException("WireGuard conf: peer Endpoint is missing")
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
    } catch (e: VirtualMachineError) {
        throw e
    } catch (e: ThreadDeath) {
        throw e
    } catch (e: LinkageError) {
        throw e
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

    private fun parseAwgParams(iface: Map<String, String>): AwgParams {
        val i1Hex = extractHex(iface["i1"])
        val i2Hex = extractHex(iface["i2"])
        val i3Hex = extractHex(iface["i3"])
        val i4Hex = extractHex(iface["i4"])
        val i5Hex = extractHex(iface["i5"])
        return AwgParams(
            junkPacketCount = iface["jc"]?.toIntOrNull() ?: AwgParams.DEFAULT_JC,
            junkPacketMinSize = iface["jmin"]?.toIntOrNull() ?: AwgParams.DEFAULT_JMIN,
            junkPacketMaxSize = iface["jmax"]?.toIntOrNull() ?: AwgParams.DEFAULT_JMAX,
            initPacketJunkSize = iface["s1"]?.toIntOrNull() ?: AwgParams.DEFAULT_S1,
            responsePacketJunkSize = iface["s2"]?.toIntOrNull() ?: AwgParams.DEFAULT_S2,
            underloadPacketJunkSize = iface["s3"]?.toIntOrNull() ?: AwgParams.DEFAULT_S3,
            payloadPacketJunkSize = iface["s4"]?.toIntOrNull() ?: AwgParams.DEFAULT_S4,
            initPacketMagicHeader = iface["h1"]?.toLongOrNull() ?: AwgParams.DEFAULT_H1,
            responsePacketMagicHeader = iface["h2"]?.toLongOrNull() ?: AwgParams.DEFAULT_H2,
            cookieReplyMagicHeader = iface["h3"]?.toLongOrNull() ?: AwgParams.DEFAULT_H3,
            transportMagicHeader = iface["h4"]?.toLongOrNull() ?: AwgParams.DEFAULT_H4,
            payloadPacketSizeCount1 = intOrDefault(i1Hex, iface["i1"], AwgParams.DEFAULT_I1),
            payloadPacketSizeCount2 = intOrDefault(i2Hex, iface["i2"], AwgParams.DEFAULT_I2),
            specialJunk3 = intOrDefault(i3Hex, iface["i3"], AwgParams.DEFAULT_I3),
            specialJunk4 = intOrDefault(i4Hex, iface["i4"], AwgParams.DEFAULT_I4),
            payloadPacketSizeCount3 = intOrDefault(i5Hex, iface["i5"], AwgParams.DEFAULT_I5),
            payloadHexI1 = i1Hex,
            payloadHexI2 = i2Hex,
            payloadHexI3 = i3Hex,
            payloadHexI4 = i4Hex,
            payloadHexI5 = i5Hex,
        )
    }

    private fun intOrDefault(hex: String?, raw: String?, default: Int): Int {
        if (hex != null) return default
        return raw?.toIntOrNull() ?: default
    }

    private fun extractHex(raw: String?): String? {
        val v = raw?.trim() ?: return null
        val wrapped = v.startsWith("<b ", ignoreCase = true) && v.endsWith(">")
        val zeroXPrefixed = v.startsWith("0x", ignoreCase = true)
        if (!wrapped && !zeroXPrefixed) return null
        val inner = if (wrapped) v.substring(3, v.length - 1).trim() else v
        val stripped = inner.removePrefix("0x").removePrefix("0X")
        if (stripped.length < 2 || stripped.length % 2 != 0) return null
        if (!stripped.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return stripped.lowercase()
    }
}
