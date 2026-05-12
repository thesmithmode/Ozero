package ru.ozero.app.ui.strategy

data class StrategyTestSettings(
    val requestsPerDomain: Int = 1,
    val concurrentLimit: Int = 20,
    val timeoutSeconds: Int = 5,
    val delayBetweenMs: Long = 500L,
    val sniDomain: String = "google.com",
    val useCustomStrategies: Boolean = false,
    val customStrategies: String = "",
)
