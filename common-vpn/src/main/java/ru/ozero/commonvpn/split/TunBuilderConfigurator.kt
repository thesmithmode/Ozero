package ru.ozero.commonvpn.split

import android.net.VpnService
import android.util.Log
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.SplitTunnelMode

class TunBuilderConfigurator(
    private val packageName: String,
) {

    fun apply(
        builder: VpnService.Builder,
        config: SplitTunnelConfig,
    ): VpnService.Builder {
        when (config.mode) {
            SplitTunnelMode.ALL -> {
                builder.addRoute("0.0.0.0", 0)
                Log.i(TAG, "split-tunnel ALL — default route v4")
            }
            SplitTunnelMode.BYPASS_LAN -> {
                for (cidr in LanRoutes.BYPASS_LAN_IPV4) {
                    builder.addRoute(cidr.address, cidr.prefix)
                }
                Log.i(TAG, "split-tunnel BYPASS_LAN — ${LanRoutes.BYPASS_LAN_IPV4.size} v4 routes")
            }
            SplitTunnelMode.ALLOWLIST -> {
                builder.addRoute("0.0.0.0", 0)
                applyAllowed(builder, config.allowlist)
            }
            SplitTunnelMode.BLOCKLIST -> {
                builder.addRoute("0.0.0.0", 0)
                applyDisallowed(builder, config.blocklist)
            }
        }
        return builder
    }

    private fun applyAllowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        var failed = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addAllowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { failed++ }
        }
        if (failed > 0) PersistentLoggers.warn(TAG, "addAllowedApplication failed для $failed пакетов")
        runCatching { builder.addAllowedApplication(packageName) }
            .onFailure { PersistentLoggers.warn(TAG, "addAllowedApplication self failed: ${it.message}") }
        Log.i(TAG, "ALLOWLIST применён: $added пакетов")
    }

    private fun applyDisallowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        var failed = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addDisallowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { failed++ }
        }
        if (failed > 0) PersistentLoggers.warn(TAG, "addDisallowedApplication failed для $failed пакетов")
        Log.i(TAG, "BLOCKLIST применён: $added пакетов")
    }

    private companion object {
        const val TAG = "TunBuilderConfigurator"
    }
}
