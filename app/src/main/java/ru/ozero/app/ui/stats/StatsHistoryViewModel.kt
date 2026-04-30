package ru.ozero.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import javax.inject.Inject

@HiltViewModel
class StatsHistoryViewModel @Inject constructor(
    private val dao: SessionStatsDao,
) : ViewModel() {

    val sessions: StateFlow<List<SessionStatsEntity>> =
        dao.observeRecent(limit = HISTORY_LIMIT).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private companion object {
        const val HISTORY_LIMIT: Int = 30
    }
}
