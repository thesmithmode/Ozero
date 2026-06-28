package ru.ozero.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import javax.inject.Inject

@HiltViewModel
class TrafficStatsViewModel @Inject constructor(
    private val dao: SessionStatsDao,
) : ViewModel() {

    private val timeframeRef = MutableStateFlow(TrafficTimeframe.WEEK)
    private val engineFilterRef = MutableStateFlow<Set<String>>(emptySet())
    private val sessionsExpandedRef = MutableStateFlow(false)
    private val sessionSortRef = MutableStateFlow(SessionSort.TIME_DESC)

    val timeframe: StateFlow<TrafficTimeframe> = timeframeRef.asStateFlow()
    val engineFilter: StateFlow<Set<String>> = engineFilterRef.asStateFlow()
    val sessionsExpanded: StateFlow<Boolean> = sessionsExpandedRef.asStateFlow()
    val sessionSort: StateFlow<SessionSort> = sessionSortRef.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawSessions: StateFlow<List<SessionStatsEntity>> =
        timeframeRef.flatMapLatest { tf ->
            val since = tf.periodMs?.let { System.currentTimeMillis() - it }
            if (since != null) {
                dao.observeFrom(since, STATS_HISTORY_LIMIT)
            } else {
                dao.observeAll(STATS_HISTORY_LIMIT)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    val availableEngines: StateFlow<List<String>> =
        rawSessions.map { list ->
            list.map { it.engineId }.distinct().sorted()
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    private val filteredSessions: StateFlow<List<SessionStatsEntity>> =
        combine(rawSessions, engineFilterRef) { sessions, engines ->
            if (engines.isEmpty()) sessions else sessions.filter { it.engineId in engines }
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val summary: StateFlow<TrafficSummary> =
        filteredSessions.map { sessions ->
            TrafficSummary(
                totalRx = sessions.sumOf { it.rxBytes },
                totalTx = sessions.sumOf { it.txBytes },
                sessionCount = sessions.size,
                totalDurationMs = sessions.sumOf { it.durationMs },
            )
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = TrafficSummary(0L, 0L, 0, 0L),
            )

    val engineSummaries: StateFlow<List<EngineSummary>> =
        filteredSessions.map { sessions ->
            sessions.groupBy { it.engineId }
                .map { (engineId, group) ->
                    EngineSummary(
                        engineId = engineId,
                        rx = group.sumOf { it.rxBytes },
                        tx = group.sumOf { it.txBytes },
                        sessionCount = group.size,
                    )
                }
                .sortedByDescending { it.rx + it.tx }
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val chartData: StateFlow<TrafficChartData> =
        combine(filteredSessions, timeframeRef) { sessions, tf ->
            buildChartData(sessions, tf, System.currentTimeMillis())
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = TrafficChartData.Empty,
            )

    val sessions: StateFlow<List<SessionStatsEntity>> =
        combine(filteredSessions, sessionSortRef) { sessions, sort ->
            applySortForDrillDown(sessions, sort)
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    fun setTimeframe(tf: TrafficTimeframe) {
        timeframeRef.value = tf
        engineFilterRef.value = emptySet()
    }

    fun toggleEngineFilter(engineId: String) {
        engineFilterRef.value = engineFilterRef.value.toMutableSet().also { set ->
            if (engineId in set) set.remove(engineId) else set.add(engineId)
        }
    }

    fun clearEngineFilter() {
        engineFilterRef.value = emptySet()
    }

    fun setSessionsExpanded(expanded: Boolean) {
        sessionsExpandedRef.value = expanded
    }

    fun setSessionSort(sort: SessionSort) {
        sessionSortRef.value = sort
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    fun clearSessions() {
        viewModelScope.launch {
            dao.deleteCompleted()
        }
    }

    private companion object {
        const val STATS_HISTORY_LIMIT = 2_000

        fun buildChartData(
            sessions: List<SessionStatsEntity>,
            timeframe: TrafficTimeframe,
            nowMs: Long,
        ): TrafficChartData {
            if (sessions.isEmpty()) return TrafficChartData.Empty

            val bucketMs = timeframe.bucketMs
            val minTs = sessions.minOf { it.startedAt }
            val maxTs = sessions.maxOf { it.startedAt }
            val firstBucket = timeframe.periodMs
                ?.let { ((nowMs - it) / bucketMs) * bucketMs }
                ?: ((minTs / bucketMs) * bucketMs)
            val lastBucket = timeframe.periodMs
                ?.let { (nowMs / bucketMs) * bucketMs }
                ?: ((maxTs / bucketMs) * bucketMs)
            val rawBuckets = generateSequence(firstBucket) { it + bucketMs }
                .takeWhile { it <= lastBucket }
                .toList()
            val buckets = if (rawBuckets.size >= 2) rawBuckets else rawBuckets + (firstBucket + bucketMs)

            if (buckets.isEmpty()) return TrafficChartData.Empty

            val bucketIndex: Map<Long, Int> = buckets.withIndex().associate { (i, b) -> b to i }

            val perEngine = mutableMapOf<String, LongArray>()
            val allLine = LongArray(buckets.size)

            for (session in sessions) {
                val b = (session.startedAt / bucketMs) * bucketMs
                val idx = bucketIndex[b] ?: continue
                val total = session.rxBytes + session.txBytes
                allLine[idx] += total
                perEngine.getOrPut(session.engineId) { LongArray(buckets.size) }[idx] += total
            }

            val lines = mutableMapOf<String, List<Long>>()
            lines[ENGINE_ID_ALL] = allLine.asList()
            perEngine.forEach { (id, arr) -> lines[id] = arr.asList() }

            return TrafficChartData(buckets = buckets, lines = lines)
        }

        fun applySortForDrillDown(
            sessions: List<SessionStatsEntity>,
            sort: SessionSort,
        ): List<SessionStatsEntity> = when (sort) {
            SessionSort.TIME_DESC -> sessions.sortedByDescending { it.startedAt }
            SessionSort.TIME_ASC -> sessions.sortedBy { it.startedAt }
            SessionSort.TRAFFIC_DESC -> sessions.sortedByDescending { it.rxBytes + it.txBytes }
            SessionSort.DURATION_DESC -> sessions.sortedByDescending { it.durationMs }
        }
    }
}
