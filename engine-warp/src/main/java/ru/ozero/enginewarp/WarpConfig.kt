package ru.ozero.enginewarp

data class WarpConfig(
    val privateKey: String,
    val publicKey: String = "",
    val peerPublicKey: String,
    val peerEndpoint: String,
    val interfaceAddressV4: String,
    val interfaceAddressV6: String,
    val accountLicense: String = "",
    val mtu: Int = DEFAULT_MTU,
    val dnsServers: List<String> = DEFAULT_DNS,
    val keepaliveSeconds: Int = DEFAULT_KEEPALIVE,
    val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
    val awgParams: AwgParams = AwgParams(),
) {
    override fun toString(): String {
        val peerFingerprint = if (peerPublicKey.length >= 8) {
            "***${peerPublicKey.takeLast(8)}"
        } else {
            "***"
        }
        return "WarpConfig(privateKey=***, publicKey=$publicKey, peerPublicKey=$peerFingerprint, " +
            "peerEndpoint=$peerEndpoint, interfaceAddressV4=$interfaceAddressV4, " +
            "interfaceAddressV6=***, accountLicense=***, mtu=$mtu, " +
            "keepalive=${keepaliveSeconds}s, awg=$awgParams)"
    }

    companion object {
        const val DEFAULT_MTU = 1280
        const val DEFAULT_KEEPALIVE = 25
        val DEFAULT_DNS = listOf("1.1.1.1", "2606:4700:4700::1111")
    }
}
