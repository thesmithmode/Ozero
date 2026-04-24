package ru.ozero.commonvpn

data class HevTunnelConfig(
    val tunFd: Int,
    val socksAddress: String,
    val socksPort: Int,
    val dnsAddress: String = "127.0.0.1",
    val dnsPort: Int = 53,
) {
    init {
        require(tunFd >= 0) { "tunFd должен быть неотрицательным" }
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(dnsPort in 1..65535) { "dnsPort вне диапазона: $dnsPort" }
        require(isSafeAddress(socksAddress)) { "socksAddress содержит недопустимые символы" }
        require(isSafeAddress(dnsAddress)) { "dnsAddress содержит недопустимые символы" }
    }

    fun toYaml(): String =
        """
        tunnel:
          fd: $tunFd
        socks5:
          address: $socksAddress
          port: $socksPort
        dns:
          address: $dnsAddress
          port: $dnsPort
        """.trimIndent()

    private companion object {
        // YAML-injection защита: только IP-подобные и DNS-имена, никаких newline/control-chars
        private val ADDRESS_REGEX = Regex("^[a-zA-Z0-9._:-]+$")

        fun isSafeAddress(addr: String): Boolean = ADDRESS_REGEX.matches(addr)
    }
}
