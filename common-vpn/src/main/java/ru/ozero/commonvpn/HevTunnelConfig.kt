package ru.ozero.commonvpn

/**
 * Конфиг для upstream `heiher/hev-socks5-tunnel` (2.7.x).
 *
 * Формат YAML строго соответствует upstream `conf/main.yml`:
 *   tunnel: { mtu, ipv4, ipv6 }
 *   socks5: { address, port, udp }
 *
 * fd передаётся отдельным JNI-параметром `TProxyStartService(path, fd)`,
 * НЕ через YAML — старая версия конфига писала `tunnel.fd` (поля нет в upstream
 * парсере, на этом крашился v1.0.0 первого выпуска). DNS секция в upstream
 * НЕТ — DNS-resolution делает SOCKS5-сервер на удалённой стороне.
 */
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

    /**
     * Сериализация в формате upstream `conf/main.yml` (heiher/hev-socks5-tunnel 2.7.0).
     * IPv6 в одинарных кавычках — yaml-libyaml требует quoting для строк с двоеточиями.
     */
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

        // Должны совпадать с TUN_ADDRESS / TUN_ADDRESS_V6 в OzeroVpnService —
        // hev открывает раздачу на этих IP в TUN-интерфейсе.
        private const val DEFAULT_TUN_IPV4: String = "10.10.10.10"
        private const val DEFAULT_TUN_IPV6: String = "fd00:ffff:ffff:ffff::1"
        private const val DEFAULT_UDP_MODE: String = "udp"

        // YAML-injection защита: только IP/DNS-имена, никаких newline/control-chars.
        private val ADDRESS_REGEX = Regex("^[a-zA-Z0-9._:-]+$")

        fun isSafeAddress(addr: String): Boolean = ADDRESS_REGEX.matches(addr)
    }
}
