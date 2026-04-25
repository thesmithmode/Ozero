package ru.ozero.commonvpn.split

import android.net.VpnService
import android.util.Log

/**
 * Применяет [SplitTunnelConfig] к [VpnService.Builder] до вызова `establish()`.
 * Выделено в отдельный класс ради тестируемости (тесты на mock Builder + проверка вызовов).
 */
class TunBuilderConfigurator(
    private val packageName: String,
) {

    /** Возвращает тот же builder для chain-style вызова. */
    fun apply(builder: VpnService.Builder, config: SplitTunnelConfig): VpnService.Builder {
        when (config.mode) {
            SplitTunnelMode.ALL -> {
                builder.addRoute("0.0.0.0", 0)
                Log.i(TAG, "split-tunnel ALL — добавлен default route")
            }
            SplitTunnelMode.BYPASS_LAN -> {
                for (cidr in LanRoutes.BYPASS_LAN_IPV4) {
                    builder.addRoute(cidr.address, cidr.prefix)
                }
                Log.i(TAG, "split-tunnel BYPASS_LAN — ${LanRoutes.BYPASS_LAN_IPV4.size} routes")
            }
            SplitTunnelMode.ALLOWLIST -> {
                builder.addRoute("0.0.0.0", 0)
                applyAllowed(builder, config.packages)
            }
            SplitTunnelMode.BLOCKLIST -> {
                builder.addRoute("0.0.0.0", 0)
                applyDisallowed(builder, config.packages)
            }
        }
        return builder
    }

    private fun applyAllowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        for (pkg in packages) {
            if (pkg == packageName) continue // нельзя себя в allowlist — VPN сам идёт через VPN
            runCatching { builder.addAllowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { Log.w(TAG, "addAllowedApplication failed для $pkg: ${it.message}") }
        }
        if (added == 0) {
            // Без явного addAllowedApplication() Builder направляет ВСЕ приложения в TUN,
            // что противоположно семантике ALLOWLIST. Добавляем self-пакет: VpnService
            // не направляет own traffic в собственный туннель → эффективно 0 пакетов через VPN.
            runCatching { builder.addAllowedApplication(packageName) }
                .onFailure { Log.e(TAG, "не удалось добавить self в allowlist: ${it.message}") }
            Log.w(TAG, "ALLOWLIST пуст → kill-all (только self в фильтре)")
        } else {
            Log.i(TAG, "ALLOWLIST применён: $added пакетов")
        }
    }

    private fun applyDisallowed(builder: VpnService.Builder, packages: Set<String>) {
        var added = 0
        for (pkg in packages) {
            if (pkg == packageName) continue
            runCatching { builder.addDisallowedApplication(pkg) }
                .onSuccess { added++ }
                .onFailure { Log.w(TAG, "addDisallowedApplication failed для $pkg: ${it.message}") }
        }
        Log.i(TAG, "BLOCKLIST применён: $added пакетов")
    }

    private companion object {
        const val TAG = "TunBuilderConfigurator"
    }
}
