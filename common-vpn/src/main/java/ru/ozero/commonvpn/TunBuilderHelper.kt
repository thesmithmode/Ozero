package ru.ozero.commonvpn

import android.net.VpnService
import android.os.Build
import ru.ozero.commondns.PublicDnsServers
import ru.ozero.commonvpn.split.SplitTunnelConfig
import ru.ozero.commonvpn.split.TunBuilderConfigurator
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.TunSpec

/**
 * Строит VpnService.Builder под две архитектурно разные категории движков:
 *
 * 1. `applyEngineTunSpec` — full-IP-stack engines (WARP, URnetwork) с killswitch invariant.
 *    Используют lockdown через setUnderlyingNetworks(null) — необходимо для WiFi↔Mobile
 *    транзиций (P37 incident: без lockdown TUN теряет route).
 *
 * 2. `buildTunBuilder` — local SOCKS5 proxy engines (ByeDPI) с upstream parity.
 *    НЕ применяют lockdown — upstream ByeByeDPI 1.7.5 не вызывает setUnderlyingNetworks,
 *    и наш opt-in вызов ломает QUIC/UDP routing (см. 2026-05-20 investigation,
 *    `byedpi-vpn-pipeline-upstream-divergence` concept article).
 *
 * Per-engine override через `applyUnderlying` parameter в applyLockdown.
 */
class TunBuilderHelper(
    private val service: VpnService,
    private val builderFactory: () -> VpnService.Builder = { service.Builder() },
) {

    @Suppress("UnusedParameter")
    fun applyEngineTunSpec(spec: TunSpec, ipv6Enabled: Boolean): VpnService.Builder {
        val builder = builderFactory()
            .setSession(spec.sessionName)
            .setMtu(spec.mtu)
            .setBlocking(spec.blocking)
            .addAddress(spec.ipv4Address, spec.ipv4PrefixLength)
        applyLockdown(builder, "applyEngineTunSpec", applyUnderlying = true)
        spec.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { PersistentLoggers.warn(TAG, "spec addDnsServer rejected '$dns': ${it.message}") }
        }
        // Calling only one allowFamily makes Android drop the other family in split-tunnel blocklist mode.
        builder.allowFamily(android.system.OsConstants.AF_INET)
        builder.allowFamily(android.system.OsConstants.AF_INET6)
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
        } else {
            addCidrRoutes(builder, spec.routeCidrsV4, "v4")
        }
        val v6 = spec.ipv6Address
        if (ipv6Enabled) {
            builder.addAddress(v6 ?: TUN_ADDRESS_V6, if (v6 == null) TUN_PREFIX_LENGTH_V6 else spec.ipv6PrefixLength)
            builder.addRoute("::", 0)
        } else if (spec.allowFamilyV6 && v6 != null) {
            builder.addAddress(v6, spec.ipv6PrefixLength)
            if (spec.routeAllV6) {
                builder.addRoute("::", 0)
            } else {
                addCidrRoutes(builder, spec.routeCidrsV6, "v6")
            }
        }
        return builder
    }

    fun buildTunBuilder(
        splitConfig: SplitTunnelConfig = SplitTunnelConfig(),
        ipv6Enabled: Boolean = false,
        customDnsServers: List<String> = emptyList(),
        applyUnderlying: Boolean = false,
    ): VpnService.Builder {
        val builder = builderFactory()
            .addAddress(TUN_ADDRESS, TUN_PREFIX_LENGTH)
            .setSession(SESSION_NAME)
        applyLockdown(builder, "buildTunBuilder", applyUnderlying = applyUnderlying)
        if (ipv6Enabled) {
            builder.addAddress(TUN_ADDRESS_V6, TUN_PREFIX_LENGTH_V6)
            builder.addRoute("::", 0)
        }
        // Ровно один DNS — паритет с upstream ByeByeDPI.
        // Множественные DNS дублируют lookup через TUN и тормозят resolve.
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

    /**
     * Per-engine lockdown через setUnderlyingNetworks(null).
     *
     * applyUnderlying=true (WARP/URnetwork): killswitch invariant. Без вызова при
     * WiFi→Mobile транзиции Android освобождает старый underlying network → TUN теряет
     * route → lockdown breaks. P37 incident зафиксирован sentinel'ом.
     *
     * applyUnderlying=false (ByeDPI): upstream ByeByeDPI 1.7.5 parity. Вызов с null
     * ломает QUIC routing — outgoing UDP socket в byedpi process теряет authoritative
     * underlying network → kernel routes UDP packets через wrong interface → QUIC
     * handshake fail на ~10-15с после connect (YouTube fallback). TCP менее affected
     * (kernel cache route per-socket). Reasoning: `byedpi-vpn-pipeline-upstream-divergence`.
     */
    private fun applyLockdown(builder: VpnService.Builder, callerTag: String, applyUnderlying: Boolean) {
        if (!applyUnderlying) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        runCatching { builder.setUnderlyingNetworks(null) }
            .onFailure { t ->
                PersistentLoggers.warn(
                    TAG,
                    "$callerTag: setUnderlyingNetworks(null) failed: ${t.message}",
                )
            }
    }

    private fun addCidrRoutes(builder: VpnService.Builder, cidrs: List<String>, familyTag: String) {
        cidrs.forEach { cidr ->
            val address = cidr.substringBefore('/').trim()
            val prefix = cidr.substringAfter('/', missingDelimiterValue = "").trim().toIntOrNull()
            if (address.isBlank() || prefix == null) {
                PersistentLoggers.warn(TAG, "skip invalid $familyTag route")
                return@forEach
            }
            runCatching { builder.addRoute(address, prefix) }
                .onFailure { PersistentLoggers.warn(TAG, "addRoute $familyTag failed: ${it.message}") }
        }
    }

    @Suppress("UnusedPrivateMember")
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
