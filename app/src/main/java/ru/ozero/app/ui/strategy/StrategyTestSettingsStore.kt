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
        sniDomain = prefs.getString(KEY_SNI_DOMAIN, "google.com") ?: "google.com",
        useCustomStrategies = prefs.getBoolean(KEY_USE_CUSTOM, false),
        customStrategies = prefs.getString(KEY_CUSTOM_STRATEGIES, "") ?: "",
    )

    override fun save(settings: StrategyTestSettings) {
        prefs.edit()
            .putInt(KEY_REQUESTS_PER_DOMAIN, settings.requestsPerDomain)
            .putInt(KEY_CONCURRENT_LIMIT, settings.concurrentLimit)
            .putInt(KEY_TIMEOUT_SECONDS, settings.timeoutSeconds)
            .putLong(KEY_DELAY_BETWEEN_MS, settings.delayBetweenMs)
            .putString(KEY_SNI_DOMAIN, settings.sniDomain)
            .putBoolean(KEY_USE_CUSTOM, settings.useCustomStrategies)
            .putString(KEY_CUSTOM_STRATEGIES, settings.customStrategies)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "byedpi_proxytest"
        private const val KEY_REQUESTS_PER_DOMAIN = "byedpi_proxytest_requests_per_domain"
        private const val KEY_CONCURRENT_LIMIT = "byedpi_proxytest_concurrent_limit"
        private const val KEY_TIMEOUT_SECONDS = "byedpi_proxytest_timeout_seconds"
        private const val KEY_DELAY_BETWEEN_MS = "byedpi_proxytest_delay_between_ms"
        private const val KEY_SNI_DOMAIN = "byedpi_proxytest_sni_domain"
        private const val KEY_USE_CUSTOM = "byedpi_proxytest_use_custom"
        private const val KEY_CUSTOM_STRATEGIES = "byedpi_proxytest_custom_strategies"
    }
}
