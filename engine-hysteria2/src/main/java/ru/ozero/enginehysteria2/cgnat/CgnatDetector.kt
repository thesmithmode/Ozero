package ru.ozero.enginehysteria2.cgnat

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Поставщик локальных IP-адресов всех активных не-loopback интерфейсов.
 * Дефолт читает [NetworkInterface], в тестах подменяется лямбдой.
 */
fun interface LocalAddressProvider {
    fun list(): List<String>
}

/**
 * Пассивная CGNAT-детекция (RFC 6598). Активная (STUN) — E9.
 *
 * Правила (приоритет сверху вниз):
 *   1. любой адрес в 100.64.0.0/10 → [NatStatus.CGNAT]
 *   2. иначе любой публичный (не private/link-local/loopback/ULA) → [NatStatus.OPEN]
 *   3. иначе [NatStatus.UNKNOWN]
 */
class CgnatDetector(
    private val provider: LocalAddressProvider = LocalAddressProvider { defaultProvider() },
) {

    fun detect(): NatStatus {
        val addrs = provider.list().mapNotNull { parse(it) }
        if (addrs.isEmpty()) return NatStatus.UNKNOWN

        if (addrs.any { it is Inet4Address && it.isCgnat() }) return NatStatus.CGNAT
        if (addrs.any { it.isPublic() }) return NatStatus.OPEN
        return NatStatus.UNKNOWN
    }

    private fun parse(s: String): InetAddress? = runCatching { InetAddress.getByName(s) }.getOrNull()

    private fun Inet4Address.isCgnat(): Boolean {
        val b = address
        // 100.64.0.0/10 → первый байт = 100, второй байт в [64..127]
        if ((b[0].toInt() and 0xFF) != 100) return false
        val second = b[1].toInt() and 0xFF
        return second in 64..127
    }

    private fun InetAddress.isPublic(): Boolean {
        @Suppress("ComplexCondition")
        // 4 reserved-address категории — RFC 5735 / RFC 4291. Дробление в helper
        // ухудшит читаемость списка категорий.
        if (isLoopbackAddress || isLinkLocalAddress || isAnyLocalAddress || isMulticastAddress) return false
        if (isSiteLocalAddress) return false // RFC1918 для v4, fec0::/10 (deprecated) для v6
        if (this is Inet4Address && isCgnat()) return false
        if (this is Inet6Address) {
            val firstByte = address[0].toInt() and 0xFF
            // ULA fc00::/7
            if (firstByte and 0xFE == 0xFC) return false
        }
        return true
    }

    private companion object {
        fun defaultProvider(): List<String> {
            val out = mutableListOf<String>()
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            for (iface in ifs.toList()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr.isLoopbackAddress) continue
                    out += addr.hostAddress ?: continue
                }
            }
            return out
        }
    }
}
