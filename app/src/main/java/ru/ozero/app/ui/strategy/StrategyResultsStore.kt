package ru.ozero.app.ui.strategy

interface StrategyResultsStore {
    fun load(): List<StrategyResult>
    fun save(results: List<StrategyResult>)
}
