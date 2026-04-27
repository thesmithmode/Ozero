package ru.ozero.commonvpn

data class HevTunnelConfig(
    val tunFd: Int,
    val socksAddress: String,
    val socksPort: Int,
    val tunMtu: Int = DEFAULT_TUN_MTU,
    val tunIpv4: String = DEFAULT_TUN_IPV4,
    val tunIpv6: String = DEFAULT_TUN_IPV6,
    val udpMode: String = DEFAULT_UDP_MODE,
) {
    init {
        require(tunFd >= 0) { "tunFd должен быть неотрицательным" }
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(tunMtu in 576..65535) { "tunMtu вне разумного диапазона: $tunMtu" }
        require(isSafeAddress(socksAddress)) { "socksAddress содержит недопустимые символы" }
        require(isSafeAddress(tunIpv4)) { "tunIpv4 содержит недопустимые символы" }
        require(isSafeAddress(tunIpv6)) { "tunIpv6 содержит недопустимые символы" }
        require(udpMode in setOf("udp", "tcp")) { "udpMode должен быть udp или tcp: $udpMode" }
    }

    fun toYaml(): String =
        """
        tunnel:
          mtu: $tunMtu
          ipv4: $tunIpv4
          ipv6: '$tunIpv6'
        socks5:
          address: $socksAddress
          port: $socksPort
          udp: '$udpMode'
        """.trimIndent() + "\n"

    private companion object {
        private const val DEFAULT_TUN_MTU: Int = 1500

        private const val DEFAULT_TUN_IPV4: String = "10.10.10.10"
        private const val DEFAULT_TUN_IPV6: String = "fd00:ffff:ffff:ffff::1"
        private const val DEFAULT_UDP_MODE: String = "udp"

        private val ADDRESS_REGEX = Regex("^[a-zA-Z0-9._:-]+$")

        fun isSafeAddress(addr: String): Boolean = ADDRESS_REGEX.matches(addr)
    }
}
