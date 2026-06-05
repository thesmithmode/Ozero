package ru.ozero.enginewarp

import java.security.MessageDigest

class WarpRuntimeFingerprint(
    private val slotId: String,
    private val digest: String,
) {
    override fun equals(other: Any?): Boolean = other is WarpRuntimeFingerprint && digest == other.digest

    override fun hashCode(): Int = digest.hashCode()

    override fun toString(): String = "WarpRuntimeFingerprint(slotId=$slotId, digest=***)"
}

fun WarpConfigSlot.runtimeFingerprint(): WarpRuntimeFingerprint =
    WarpRuntimeFingerprint(
        slotId = id,
        digest = sha256Runtime(
            listOf(
                config.privateKey,
                config.publicKey,
                config.peerPublicKey,
                config.peerEndpoint,
                config.interfaceAddressV4,
                config.interfaceAddressV6,
                config.accountLicense,
                config.mtu.toString(),
                config.dnsServers.joinToString("\n"),
                config.keepaliveSeconds.toString(),
                config.allowedIps.joinToString("\n"),
                config.awgParams.toString(),
                config.doHProvider.name,
                canonicalRawIni(rawIniOverride),
                canonicalEndpoints(endpointList),
            ),
        ),
    )

private fun canonicalRawIni(rawIniOverride: String?): String {
    val raw = rawIniOverride?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val known = runCatching {
        WarpConfParser.parse(raw).getOrThrow().let { WarpIniBuilder.build(it) }
    }.getOrElse { return raw }
    val extras = extractRawExtras(raw)
    return if (extras.isEmpty()) known else known + "\n" + extras.joinToString("\n")
}

private fun canonicalEndpoints(endpointList: List<String>): String =
    endpointList.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .joinToString("\n")

private fun extractRawExtras(raw: String): List<String> {
    val knownInterfaceKeys = setOf(
        "privatekey",
        "address",
        "dns",
        "mtu",
        "jc",
        "jmin",
        "jmax",
        "s1",
        "s2",
        "s3",
        "s4",
        "h1",
        "h2",
        "h3",
        "h4",
    )
    val knownPeerKeys = setOf(
        "publickey",
        "allowedips",
        "endpoint",
        "persistentkeepalive",
    )
    val extras = mutableListOf<String>()
    var section: String? = null
    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith('[') && line.endsWith(']')) {
            section = line.substring(1, line.length - 1).trim().lowercase().takeIf { it.isNotBlank() }
            if (section !in setOf("interface", "peer")) {
                extras.add(line)
            }
            return@forEach
        }
        val eq = line.indexOf('=')
        if (eq <= 0) {
            if (section !in setOf("interface", "peer")) extras.add(line)
            return@forEach
        }
        val key = line.substring(0, eq).trim().lowercase()
        val keep = when (section) {
            "interface" -> key !in knownInterfaceKeys
            "peer" -> key !in knownPeerKeys
            else -> true
        }
        if (keep) extras.add(line)
    }
    return extras
}

private fun sha256Runtime(parts: List<String>): String {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        md.update(part.toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
    }
    return md.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
