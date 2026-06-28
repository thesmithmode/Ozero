package ru.ozero.app.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.corestorage.dao.ServerDao
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val dao: ServerDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ServersUiState>(ServersUiState.Loading)
    val uiState: StateFlow<ServersUiState> = _uiState.asStateFlow()

    private val entryId = MutableStateFlow<String?>(null)
    private val exitId = MutableStateFlow<String?>(null)

    init {
        combine(dao.observeAll(), entryId, exitId) { servers, entry, exit ->
            when {
                servers.isEmpty() -> ServersUiState.Empty
                else -> ServersUiState.Content(
                    servers = servers,
                    entryId = entry,
                    exitId = exit,
                )
            }
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onEntrySelect(id: String) {
        entryId.value = id
    }

    fun onExitSelect(id: String) {
        exitId.value = id
    }

    fun onSavePair() {
        val state = _uiState.value as? ServersUiState.Content ?: return
        if (!state.canSave) return
        val entry = state.entry ?: return
        val exit = state.exit ?: return
        viewModelScope.launch {
            dao.upsertPair(
                entry = entry.copy(role = "entry", pairId = exit.id),
                exit = exit.copy(role = "exit", pairId = entry.id),
            )
        }
    }

    fun onClearPair() {
        val state = _uiState.value as? ServersUiState.Content ?: return
        val entry = state.entry ?: return
        val exit = state.exit ?: return
        viewModelScope.launch {
            dao.upsertPair(
                entry = entry.copy(pairId = null),
                exit = exit.copy(pairId = null),
            )
        }
    }
}
