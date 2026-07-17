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
) {
    fun normalized(): StrategyTestSettings {
        val populationSize = evolutionPopulationSize.coerceIn(
            StrategyTestSettingsLimits.MIN_EVOLUTION_POPULATION_SIZE,
            StrategyTestSettingsLimits.MAX_EVOLUTION_POPULATION_SIZE,
        )
        return copy(
            requestsPerDomain = requestsPerDomain.coerceIn(1, 20),
            concurrentLimit = concurrentLimit.coerceIn(1, 50),
            timeoutSeconds = timeoutSeconds.coerceIn(1, 15),
            delayBetweenMs = delayBetweenMs.coerceIn(0L, 5_000L),
            evolutionPopulationSize = populationSize,
            evolutionMaxGenerations = evolutionMaxGenerations.coerceIn(1, 100),
            evolutionMutationRate = evolutionMutationRate.normalizedUnit(defaultValue = 0.2f),
            evolutionEliteCount = evolutionEliteCount.coerceIn(1, populationSize),
            evolutionTargetFitness = evolutionTargetFitness.normalizedUnit(defaultValue = 0.85f),
        )
    }

    private fun Float.normalizedUnit(defaultValue: Float): Float = if (isFinite()) coerceIn(0f, 1f) else defaultValue
}

object StrategyTestSettingsLimits {
    const val MIN_EVOLUTION_POPULATION_SIZE = 1
    const val MAX_EVOLUTION_POPULATION_SIZE = 200
}
