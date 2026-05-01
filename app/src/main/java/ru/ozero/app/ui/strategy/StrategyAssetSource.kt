package ru.ozero.app.ui.strategy

interface StrategyAssetSource {
    fun loadStrategies(): List<String>
    fun loadSites(): List<String>
}
