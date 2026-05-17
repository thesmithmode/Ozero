package ru.ozero.app.ui.strategy

data class StrategyTestSettings(
    val requestsPerDomain: Int = 1,
    val concurrentLimit: Int = 20,
    val timeoutSeconds: Int = 5,
    val delayBetweenMs: Long = 500L,
    val useCustomStrategies: Boolean = false,
    val customStrategies: String = "",
    val evolutionMode: Boolean = true,
    val evolutionPopulationSize: Int = 30,
    val evolutionMaxGenerations: Int = 20,
    val evolutionMutationRate: Float = 0.2f,
    val evolutionEliteCount: Int = 3,
    val evolutionTargetFitness: Float = 0.85f,
)
