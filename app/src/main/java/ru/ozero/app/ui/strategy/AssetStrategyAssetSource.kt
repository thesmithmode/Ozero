package ru.ozero.app.ui.strategy

import android.content.Context
import ru.ozero.enginebyedpi.strategy.ByeDpiStrategiesParser

class AssetStrategyAssetSource(
    private val context: Context,
    private val sniValue: String = ByeDpiStrategiesParser.DEFAULT_SNI,
    private val strategiesAsset: String = STRATEGIES_ASSET,
    private val sitesAsset: String = SITES_ASSET,
) : StrategyAssetSource {

    override fun loadStrategies(): List<String> {
        val text = context.assets.open(strategiesAsset).bufferedReader().use { it.readText() }
        return ByeDpiStrategiesParser.parse(text, sniValue).map { it.command }
    }

    override fun loadSites(): List<String> {
        val text = context.assets.open(sitesAsset).bufferedReader().use { it.readText() }
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private companion object {
        const val STRATEGIES_ASSET: String = "proxytest_strategies.list"
        const val SITES_ASSET: String = "proxytest_general.sites"
    }
}
