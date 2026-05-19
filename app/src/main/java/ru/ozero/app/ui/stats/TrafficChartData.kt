package ru.ozero.app.ui.stats

const val ENGINE_ID_ALL = "__all__"

data class TrafficChartData(
    val buckets: List<Long>,
    val lines: Map<String, List<Long>>,
) {
    companion object {
        val Empty = TrafficChartData(emptyList(), emptyMap())
    }
}

data class EngineSummary(
    val engineId: String,
    val rx: Long,
    val tx: Long,
    val sessionCount: Int,
)

data class TrafficSummary(
    val totalRx: Long,
    val totalTx: Long,
    val sessionCount: Int,
    val totalDurationMs: Long,
)
