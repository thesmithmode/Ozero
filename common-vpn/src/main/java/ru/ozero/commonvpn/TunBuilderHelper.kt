package ru.ozero.commonvpn

import android.net.VpnService
import android.os.Build
import ru.ozero.commondns.PublicDnsServers
import ru.ozero.commonvpn.split.SplitTunnelConfig
import ru.ozero.commonvpn.split.TunBuilderConfigurator
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.TunSpec

class TunBuilderHelper(private val service: VpnService) {

    fun applyEngineTunSpec(spec: TunSpec, ipv6Enabled: Boolean): VpnService.Builder {
        val builder = service.Builder()
            .setSession(spec.sessionName)
            .setMtu(spec.mtu)
            .setBlocking(spec.blocking)
            .addAddress(spec.ipv4Address, spec.ipv4PrefixLength)
        applyLockdown(builder, "applyEngineTunSpec", applyUnderlying = true)
        spec.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { PersistentLoggers.warn(TAG, "spec addDnsServer rejected '$dns': ${it.message}") }
        }
        builder.allowFamily(android.system.OsConstants.AF_INET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { builder.setMetered(false) }
        }
        if (spec.routeAllV4) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && spec.excludeRfc1918) {
                builder.addRoute("0.0.0.0", 0)
                runCatching {
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("10.0.0.0"), 8),
                    )
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("172.16.0.0"), 12),
                    )
                    builder.excludeRoute(
                        android.net.IpPrefix(java.net.InetAddress.getByName("192.168.0.0"), 16),
                    )
                }.onFailure { PersistentLoggers.warn(TAG, "excludeRoute RFC1918 failed: ${it.message}") }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }
        }
        val v6 = spec.ipv6Address
        val routeIpv6 = ipv6Enabled && spec.allowFamilyV6 && v6 != null && spec.routeAllV6
        if (routeIpv6) {
            builder.allowFamily(android.system.OsConstants.AF_INET6)
            builder.addAddress(v6, spec.ipv6PrefixLength)
            builder.addRoute("::", 0)
        } else {
            blackholeIpv6(builder, "applyEngineTunSpec")
        }
        return builder
    }

    fun buildTunBuilder(
        splitConfig: SplitTunnelConfig = SplitTunnelConfig(),
        ipv6Enabled: Boolean = false,
        customDnsServers: List<String> = emptyList(),
        applyUnderlying: Boolean = false,
    ): VpnService.Builder {
        val builder = service.Builder()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .setSession(SESSION_NAME)
        applyLockdown(builder, "buildTunBuilder", applyUnderlying = applyUnderlying)
        if (ipv6Enabled) {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
        }
        val dnsServers = (if (customDnsServers.isNotEmpty()) customDnsServers else TUN_DNS_SERVERS).take(1)
        dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { PersistentLoggers.warn(TAG, "addDnsServer rejected '$dns': ${it.message}") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { builder.setMetered(false) }
        }
        TunBuilderConfigurator(service.packageName).apply(builder, splitConfig, excludeSelf = true)
        return builder
    }

    private fun applyLockdown(builder: VpnService.Builder, callerTag: String, applyUnderlying: Boolean) {
        if (!applyUnderlying) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            runCatching { builder.setUnderlyingNetworks(null) }
                .onFailure { t ->
                    PersistentLoggers.warn(
                        TAG,
                        "$callerTag: setUnderlyingNetworks(null) failed: ${t.message}",
                    )
                }
        }
    }

    private fun blackholeIpv6(builder: VpnService.Builder, callerTag: String) {
        runCatching {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
        }.onFailure {
            PersistentLoggers.warn(TAG, "$callerTag: blackhole IPv6 failed: ${it.message}")
        }
    }

    companion object {
        const val TUN_ADDRESS = "10.10.10.10"
        const val TUN_PREFIX_LENGTH = 32
        const val TUN_ADDRESS_V6 = "fd00::1"
        const val TUN_PREFIX_LENGTH_V6 = 128
        val TUN_DNS_SERVERS: List<String> = PublicDnsServers.IPV4
        private const val SESSION_NAME = "Ozero"
        private const val TAG = "TunBuilderHelper"
    }
}
