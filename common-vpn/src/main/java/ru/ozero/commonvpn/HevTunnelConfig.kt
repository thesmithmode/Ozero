package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor

data class HevTunnelConfig(
    val tunPfd: ParcelFileDescriptor,
    val socksAddress: String,
    val socksPort: Int,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
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
        require(isSafeCredential(socksUsername)) { "socksUsername содержит недопустимые символы" }
        require(isSafeCredential(socksPassword)) { "socksPassword содержит недопустимые символы" }
        require((socksUsername == null) == (socksPassword == null)) { "socks auth должен быть полным" }
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
        misc:
          task-stack-size: 81920
        socks5:
          address: $socksAddress
          port: $socksPort
          udp: $udpMode
        """.trimIndent() + authYaml() + "\n"

    private fun authYaml(): String {
        val username = socksUsername ?: return ""
        val password = requireNotNull(socksPassword)
        return "\n  username: $username\n  password: $password"
    }

    companion object {
        const val DEFAULT_TUN_MTU: Int = 8500

        private const val DEFAULT_TUN_IPV4: String = "10.10.10.10"
        private const val DEFAULT_TUN_IPV6: String = "fd00::1"
        private const val DEFAULT_UDP_MODE: String = "udp"
        private const val DEFAULT_HEV_LOG_LEVEL: String = "warn"

        private val ADDRESS_REGEX = Regex("^[a-zA-Z0-9._:-]+$")
        private val CREDENTIAL_REGEX = Regex("^[a-zA-Z0-9._~-]+$")
        private val HEV_LOG_LEVELS = setOf("debug", "info", "warn", "error")

        fun isSafeAddress(addr: String): Boolean = ADDRESS_REGEX.matches(addr)

        fun isSafeCredential(value: String?): Boolean = value == null || CREDENTIAL_REGEX.matches(value)
    }
}
