package ru.ozero.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.strategy.AutoStrategyPicker
import ru.ozero.enginebyedpi.strategy.ByeDpiStrategiesParser
import ru.ozero.enginebyedpi.strategy.HttpSocksProbeClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoStrategyPickerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val byeDpiEngine: ByeDpiEngine,
) {
    fun create(socksPort: Int = DEFAULT_SOCKS_PORT, sniValue: String = DEFAULT_SNI): AutoStrategyPicker {
        val strategiesText = context.assets.open(STRATEGIES_ASSET).bufferedReader().use { it.readText() }
        val strategies = ByeDpiStrategiesParser.parse(strategiesText, sniValue)
        val sitesText = context.assets.open(SITES_ASSET).bufferedReader().use { it.readText() }
        val sites = sitesText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val probeClient = HttpSocksProbeClient(proxyPort = socksPort)
        return AutoStrategyPicker(
            byeDpiEngine = byeDpiEngine,
            probeClient = probeClient,
            strategies = strategies,
            sites = sites,
            socksPort = socksPort,
        )
    }

    private companion object {
        const val DEFAULT_SOCKS_PORT: Int = 1080
        const val DEFAULT_SNI: String = "google.com"
        const val STRATEGIES_ASSET: String = "byedpi_strategies.list"
        const val SITES_ASSET: String = "byedpi_test_sites.list"
    }
}
