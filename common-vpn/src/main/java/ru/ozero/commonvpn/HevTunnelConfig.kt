package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor

data class HevTunnelConfig(
    val tunPfd: ParcelFileDescriptor,
    val socksAddress: String,
    val socksPort: Int,
    val tunMtu: Int = DEFAULT_TUN_MTU,
    val tunIpv4: String = DEFAULT_TUN_IPV4,
    val tunIpv6: String = DEFAULT_TUN_IPV6,
    val udpMode: String = DEFAULT_UDP_MODE,
    val hevLogLevel: String = DEFAULT_HEV_LOG_LEVEL,
) {
    init {
        require(tunPfd.fd >= 0) { "tunPfd.fd должен быть неотрицательным" }
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(tunMtu in 576..65535) { "tunMtu вне разумного диапазона: $tunMtu" }
        require(isSafeAddress(socksAddress)) { "socksAddress содержит недопустимые символы" }
        require(isSafeAddress(tunIpv4)) { "tunIpv4 содержит недопустимые символы" }
        require(isSafeAddress(tunIpv6)) { "tunIpv6 содержит недопустимые символы" }
        require(udpMode in setOf("udp", "tcp")) { "udpMode должен быть udp или tcp: $udpMode" }
        require(hevLogLevel in HEV_LOG_LEVELS) {
            "hevLogLevel должен быть одним из $HEV_LOG_LEVELS: $hevLogLevel"
        }
    }

    fun toYaml(): String =
        """
        tunnel:
          mtu: $tunMtu
          ipv4: $tunIpv4
          ipv6: '$tunIpv6'
        misc:
          task-stack-size: 81920
          log-level: $hevLogLevel
        socks5:
          address: $socksAddress
          port: $socksPort
          udp: '$udpMode'
        """.trimIndent() + "\n"

    private companion object {
        private const val DEFAULT_TUN_MTU: Int = 8500

        private const val DEFAULT_TUN_IPV4: String = "10.10.10.10"
        private const val DEFAULT_TUN_IPV6: String = "fd00::1"
        private const val DEFAULT_UDP_MODE: String = "udp"
        private const val DEFAULT_HEV_LOG_LEVEL: String = "warn"

        private val ADDRESS_REGEX = Regex("^[a-zA-Z0-9._:-]+$")
        private val HEV_LOG_LEVELS = setOf("debug", "info", "warn", "error")

        fun isSafeAddress(addr: String): Boolean = ADDRESS_REGEX.matches(addr)
    }
}
