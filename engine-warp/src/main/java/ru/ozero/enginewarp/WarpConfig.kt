package ru.ozero.enginewarp

data class WarpConfig(
    val privateKey: String,
    val publicKey: String = "",
    val peerPublicKey: String,
    val peerEndpoint: String,
    val interfaceAddressV4: String,
    val interfaceAddressV6: String,
    val accountLicense: String = "",
    val mtu: Int = 1280,
    val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
) {
    override fun toString(): String =
        "WarpConfig(privateKey=***, publicKey=$publicKey, peerEndpoint=$peerEndpoint, " +
            "interfaceAddressV4=$interfaceAddressV4, interfaceAddressV6=***, " +
            "accountLicense=***, mtu=$mtu)"
}
