package ru.ozero.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import javax.inject.Inject

@HiltViewModel
class StatsHistoryViewModel @Inject constructor(
    private val dao: SessionStatsDao,
) : ViewModel() {

    private val sortRef = MutableStateFlow(SessionSort.TIME_DESC)
    private val filterRef = MutableStateFlow(SessionFilter())

    val sort: StateFlow<SessionSort> = sortRef.asStateFlow()
    val filter: StateFlow<SessionFilter> = filterRef.asStateFlow()

    private val rawSessions = dao.observeRecent(limit = HISTORY_LIMIT).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    val sessions: StateFlow<List<SessionStatsEntity>> =
        combine(rawSessions, sortRef, filterRef) { items, sortOpt, filterOpt ->
            applySessionFilterAndSort(items, filterOpt, sortOpt)
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val availableEngines: StateFlow<List<String>> =
        rawSessions.map { list -> list.map { it.engineId }.distinct().sorted() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    fun setSort(value: SessionSort) {
        sortRef.value = value
    }

    fun toggleEngineFilter(engineId: String) {
        filterRef.value = filterRef.value.let { current ->
            val next = current.engines.toMutableSet()
            if (engineId in next) next.remove(engineId) else next.add(engineId)
            current.copy(engines = next)
        }
    }

    fun setPeriod(periodMs: Long?) {
        filterRef.value = filterRef.value.copy(periodMs = periodMs)
    }

    fun clearFilters() {
        filterRef.value = SessionFilter()
    }

    private companion object {
        const val HISTORY_LIMIT: Int = 200
    }
}
