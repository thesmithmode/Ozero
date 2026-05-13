package ru.ozero.app.ui.strategy

import android.content.Context

interface StrategyTestSettingsStore {
    fun load(): StrategyTestSettings
    fun save(settings: StrategyTestSettings)
}

class SharedPrefsStrategyTestSettingsStore(context: Context) : StrategyTestSettingsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): StrategyTestSettings = StrategyTestSettings(
        requestsPerDomain = prefs.getInt(KEY_REQUESTS_PER_DOMAIN, 1),
        concurrentLimit = prefs.getInt(KEY_CONCURRENT_LIMIT, 20),
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT_SECONDS, 5),
        delayBetweenMs = prefs.getLong(KEY_DELAY_BETWEEN_MS, 500L),
        useCustomStrategies = prefs.getBoolean(KEY_USE_CUSTOM, false),
        customStrategies = (prefs.getString(KEY_CUSTOM_STRATEGIES, "") ?: "").take(MAX_CUSTOM_STRATEGIES_LEN),
        evolutionMode = prefs.getBoolean(KEY_EVOLUTION_MODE, true),
        evolutionPopulationSize = prefs.getInt(KEY_EVOLUTION_POPULATION_SIZE, 25),
        evolutionMaxGenerations = prefs.getInt(KEY_EVOLUTION_MAX_GENERATIONS, 10),
        evolutionMutationRate = prefs.getFloat(KEY_EVOLUTION_MUTATION_RATE, 0.2f),
        evolutionEliteCount = prefs.getInt(KEY_EVOLUTION_ELITE_COUNT, 5),
        evolutionTargetFitness = prefs.getFloat(KEY_EVOLUTION_TARGET_FITNESS, 0.85f),
    )

    override fun save(settings: StrategyTestSettings) {
        prefs.edit()
            .putInt(KEY_REQUESTS_PER_DOMAIN, settings.requestsPerDomain)
            .putInt(KEY_CONCURRENT_LIMIT, settings.concurrentLimit)
            .putInt(KEY_TIMEOUT_SECONDS, settings.timeoutSeconds)
            .putLong(KEY_DELAY_BETWEEN_MS, settings.delayBetweenMs)
            .putBoolean(KEY_USE_CUSTOM, settings.useCustomStrategies)
            .putString(KEY_CUSTOM_STRATEGIES, settings.customStrategies.take(MAX_CUSTOM_STRATEGIES_LEN))
            .putBoolean(KEY_EVOLUTION_MODE, settings.evolutionMode)
            .putInt(KEY_EVOLUTION_POPULATION_SIZE, settings.evolutionPopulationSize)
            .putInt(KEY_EVOLUTION_MAX_GENERATIONS, settings.evolutionMaxGenerations)
            .putFloat(KEY_EVOLUTION_MUTATION_RATE, settings.evolutionMutationRate)
            .putInt(KEY_EVOLUTION_ELITE_COUNT, settings.evolutionEliteCount)
            .putFloat(KEY_EVOLUTION_TARGET_FITNESS, settings.evolutionTargetFitness)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "byedpi_proxytest"
        private const val KEY_REQUESTS_PER_DOMAIN = "byedpi_proxytest_requests_per_domain"
        private const val KEY_CONCURRENT_LIMIT = "byedpi_proxytest_concurrent_limit"
        private const val KEY_TIMEOUT_SECONDS = "byedpi_proxytest_timeout_seconds"
        private const val KEY_DELAY_BETWEEN_MS = "byedpi_proxytest_delay_between_ms"
        private const val KEY_USE_CUSTOM = "byedpi_proxytest_use_custom"
        private const val KEY_CUSTOM_STRATEGIES = "byedpi_proxytest_custom_strategies"
        private const val KEY_EVOLUTION_MODE = "byedpi_proxytest_evolution_mode"
        private const val KEY_EVOLUTION_POPULATION_SIZE = "byedpi_proxytest_evolution_population_size"
        private const val KEY_EVOLUTION_MAX_GENERATIONS = "byedpi_proxytest_evolution_max_generations"
        private const val KEY_EVOLUTION_MUTATION_RATE = "byedpi_proxytest_evolution_mutation_rate"
        private const val KEY_EVOLUTION_ELITE_COUNT = "byedpi_proxytest_evolution_elite_count"
        private const val KEY_EVOLUTION_TARGET_FITNESS = "byedpi_proxytest_evolution_target_fitness"
        private const val MAX_CUSTOM_STRATEGIES_LEN = 16_384
    }
}
