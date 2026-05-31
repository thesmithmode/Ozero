package ru.ozero.enginewarp

import java.security.MessageDigest

data class WarpRuntimeFingerprint(
    val slotId: String,
    private val digest: String,
) {
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
                rawIniOverride.orEmpty(),
                endpointList.joinToString("\n"),
            ),
        ),
    )

private fun sha256Runtime(parts: List<String>): String {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        md.update(part.toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
    }
    return md.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
