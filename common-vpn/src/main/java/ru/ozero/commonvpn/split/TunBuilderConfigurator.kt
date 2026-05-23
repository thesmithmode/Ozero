package ru.ozero.commonvpn.split

import android.net.VpnService
import android.util.Log
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.SplitTunnelMode

class TunBuilderConfigurator(
    private val packageName: String,
) {

    // SENTINEL [feedback_split_tunnel_exclude_self]: excludeSelf=true для всех движков сверху.
    // IP-probe должен идти через EnginePlugin.ipProbeRoute(), не через split tunnel — иначе IP UI
    // показывает реальный, мимо туннеля.
    fun apply(
        builder: VpnService.Builder,
        config: SplitTunnelConfig,
        excludeSelf: Boolean = false,
    ): VpnService.Builder {
        when (config.mode) {
            SplitTunnelMode.ALL -> {
                builder.addRoute("0.0.0.0", 0)
                if (excludeSelf) excludeSelfFromTun(builder)
                Log.i(TAG, "split-tunnel ALL — default route v4, excludeSelf=$excludeSelf")
            }
            SplitTunnelMode.BYPASS_LAN -> {
                for (cidr in LanRoutes.BYPASS_LAN_IPV4) {
                    builder.addRoute(cidr.address, cidr.prefix)
                }
                if (excludeSelf) excludeSelfFromTun(builder)
                Log.i(
                    TAG,
                    "split-tunnel BYPASS_LAN — ${LanRoutes.BYPASS_LAN_IPV4.size} v4 routes, excludeSelf=$excludeSelf",
                )
            }
            SplitTunnelMode.ALLOWLIST -> {
                builder.addRoute("0.0.0.0", 0)
                applyAllowed(builder, config.allowlist, includeSelf = !excludeSelf)
            }
            SplitTunnelMode.BLOCKLIST -> {
                builder.addRoute("0.0.0.0", 0)
                if (excludeSelf) excludeSelfFromTun(builder)
                applyDisallowed(builder, config.blocklist)
            }
        }
        return builder
    }

    private fun excludeSelfFromTun(builder: VpnService.Builder) {
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { PersistentLoggers.error(TAG, "addDisallowedApplication self failed: ${it.message}") }
    }

    private fun applyAllowed(builder: VpnService.Builder, packages: Set<String>, includeSelf: Boolean = false) {
        var added = 0
        var failed = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addAllowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { failed++ }
        }
        if (failed > 0) PersistentLoggers.warn(TAG, "addAllowedApplication failed для $failed пакетов")
        if (added == 0 && !includeSelf) {
            runCatching { builder.addAllowedApplication(packageName) }
                .onFailure { PersistentLoggers.error(TAG, "не удалось добавить self в allowlist: ${it.message}") }
            PersistentLoggers.warn(TAG, "ALLOWLIST пуст → kill-all (только self в фильтре)")
        } else {
            Log.i(TAG, "ALLOWLIST применён: $added пакетов")
        }
        if (includeSelf) {
            runCatching { builder.addAllowedApplication(packageName) }
                .onFailure { PersistentLoggers.warn(TAG, "addAllowedApplication self failed: ${it.message}") }
        }
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
