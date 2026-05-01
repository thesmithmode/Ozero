package ru.ozero.app.ui.strategy

data class StrategyResult(
    val command: String,
    val successCount: Int = 0,
    val totalRequests: Int = 0,
    val currentProgress: Int = 0,
    val isCompleted: Boolean = false,
) {
    val successPercentage: Int
        get() = if (totalRequests > 0) (successCount * 100) / totalRequests else 0
}
