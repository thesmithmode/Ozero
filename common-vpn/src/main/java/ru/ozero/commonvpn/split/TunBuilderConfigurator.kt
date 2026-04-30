package ru.ozero.commonvpn.split

import android.net.VpnService
import ru.ozero.enginescore.PersistentLoggers

class TunBuilderConfigurator(
    private val packageName: String,
) {

    fun apply(builder: VpnService.Builder, config: SplitTunnelConfig): VpnService.Builder {
        when (config.mode) {
            SplitTunnelMode.ALL -> {
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute(IPV6_DEFAULT, 0)
                excludeSelfFromTun(builder)
                PersistentLoggers.info(TAG, "split-tunnel ALL — добавлен default route v4+v6, self исключён")
            }
            SplitTunnelMode.BYPASS_LAN -> {
                for (cidr in LanRoutes.BYPASS_LAN_IPV4) {
                    builder.addRoute(cidr.address, cidr.prefix)
                }
                builder.addRoute(IPV6_GLOBAL_UNICAST, IPV6_GLOBAL_UNICAST_PREFIX)
                excludeSelfFromTun(builder)
                PersistentLoggers.info(
                    TAG,
                    "split-tunnel BYPASS_LAN — ${LanRoutes.BYPASS_LAN_IPV4.size} v4 + 2000::/3 v6, self исключён",
                )
            }
            SplitTunnelMode.ALLOWLIST -> {
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute(IPV6_DEFAULT, 0)
                applyAllowed(builder, config.packages)
            }
            SplitTunnelMode.BLOCKLIST -> {
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute(IPV6_DEFAULT, 0)
                excludeSelfFromTun(builder)
                applyDisallowed(builder, config.packages)
            }
        }
        return builder
    }

    private fun excludeSelfFromTun(builder: VpnService.Builder) {
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { PersistentLoggers.error(TAG, "addDisallowedApplication self failed: ${it.message}") }
    }

    private fun applyAllowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addAllowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { PersistentLoggers.warn(TAG, "addAllowedApplication failed для $pkg: ${it.message}") }
        }
        if (added == 0) {
            runCatching { builder.addAllowedApplication(packageName) }
                .onFailure { PersistentLoggers.error(TAG, "не удалось добавить self в allowlist: ${it.message}") }
            PersistentLoggers.warn(TAG, "ALLOWLIST пуст → kill-all (только self в фильтре)")
        } else {
            PersistentLoggers.info(TAG, "ALLOWLIST применён: $added пакетов")
        }
    }

    private fun applyDisallowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addDisallowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { PersistentLoggers.warn(TAG, "addDisallowedApplication failed для $pkg: ${it.message}") }
        }
        PersistentLoggers.info(TAG, "BLOCKLIST применён: $added пакетов")
    }

    private companion object {
        const val TAG = "TunBuilderConfigurator"
        const val IPV6_DEFAULT = "::"
        const val IPV6_GLOBAL_UNICAST = "2000::"
        const val IPV6_GLOBAL_UNICAST_PREFIX = 3
    }
}
