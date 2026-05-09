package ru.ozero.app.ui.stats

import ru.ozero.corestorage.entity.SessionStatsEntity

enum class SessionSort {
    TIME_DESC,
    TIME_ASC,
    TRAFFIC_DESC,
    DURATION_DESC,
}

data class SessionFilter(
    val engines: Set<String> = emptySet(),
    val periodMs: Long? = null,
)

internal fun applySessionFilterAndSort(
    sessions: List<SessionStatsEntity>,
    filter: SessionFilter,
    sort: SessionSort,
    nowMs: Long = System.currentTimeMillis(),
): List<SessionStatsEntity> {
    val periodCutoff = filter.periodMs?.let { nowMs - it }
    val filtered = sessions.asSequence()
        .filter { filter.engines.isEmpty() || it.engineId in filter.engines }
        .filter { periodCutoff == null || it.startedAt >= periodCutoff }
    val ordered = when (sort) {
        SessionSort.TIME_DESC -> filtered.sortedByDescending { it.startedAt }
        SessionSort.TIME_ASC -> filtered.sortedBy { it.startedAt }
        SessionSort.TRAFFIC_DESC -> filtered.sortedByDescending { it.rxBytes + it.txBytes }
        SessionSort.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
    }
    return ordered.toList()
}
