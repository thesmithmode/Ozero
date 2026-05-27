package ru.ozero.enginescore

data class WireGuardOutboundConfig(
    val privateKey: String,
    val peerPublicKey: String,
    val serverHost: String,
    val serverPort: Int,
    val localAddresses: List<String>,
    val mtu: Int = DEFAULT_MTU,
    val keepaliveSeconds: Int = DEFAULT_KEEPALIVE,
) {
    override fun toString(): String =
        "WireGuardOutboundConfig(server=$serverHost:$serverPort, mtu=$mtu, addrs=${localAddresses.size})"

    companion object {
        const val DEFAULT_MTU = 1280
        const val DEFAULT_KEEPALIVE = 25
    }
}
