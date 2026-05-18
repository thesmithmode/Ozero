package ru.ozero.app.ui.stats

import ru.ozero.app.R

private const val H1_MS: Long = 3_600_000L
private const val D1_MS: Long = 24L * H1_MS
private const val W1_MS: Long = 7L * D1_MS
private const val D30_MS: Long = 30L * D1_MS
private const val Y1_MS: Long = 365L * D1_MS

enum class TrafficTimeframe(
    val labelRes: Int,
    val periodMs: Long?,
    val bucketMs: Long,
) {
    DAY(R.string.stats_tf_day, D1_MS, H1_MS),
    WEEK(R.string.stats_tf_week, W1_MS, D1_MS),
    MONTH(R.string.stats_tf_month, D30_MS, D1_MS),
    YEAR(R.string.stats_tf_year, Y1_MS, D30_MS),
    ALL(R.string.stats_tf_all, null, D30_MS),
}
