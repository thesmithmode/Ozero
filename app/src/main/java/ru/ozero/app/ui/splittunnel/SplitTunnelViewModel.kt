package ru.ozero.app.ui.splittunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import javax.inject.Inject

@HiltViewModel
class SplitTunnelViewModel @Inject constructor(
    private val appListProvider: AppListProvider,
    private val dao: AppSplitRuleDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplitTunnelUiState>(SplitTunnelUiState.Loading)
    val uiState: StateFlow<SplitTunnelUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
        viewModelScope.launch { apps.value = appListProvider.loadApps() }
        combine(
            apps,
            dao.observeAll(),
            settingsRepository.settings.map { it.splitMode },
            query,
        ) { appsList, rules, mode, q ->
            val included = rules.map { it.packageName }.toSet()
            val filtered = if (q.isBlank()) appsList else appsList.filter { it.matches(q) }
            SplitTunnelUiState.Content(
                mode = mode,
                query = q,
                apps = filtered.map { app ->
                    AppRow(
                        packageName = app.packageName,
                        label = app.label,
                        isSystem = app.isSystem,
                        included = app.packageName in included,
                        icon = app.icon,
                    )
                },
                selectedCount = included.size,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onModeChange(mode: SplitTunnelMode) {
        viewModelScope.launch { settingsRepository.setSplitMode(mode) }
    }

    fun onToggleApp(packageName: String, checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                dao.upsert(AppSplitRule(packageName = packageName, isExcluded = false))
            } else {
                dao.delete(packageName)
            }
        }
    }

    fun onQuery(value: String) {
        query.value = value
    }

    fun onClearAll() {
        viewModelScope.launch {
            val snapshot = runCatching { dao.observeAll().first() }.getOrNull() ?: return@launch
            snapshot.forEach { dao.delete(it.packageName) }
        }
    }

    private fun InstalledApp.matches(q: String): Boolean {
        val needle = q.lowercase()
        return label.lowercase().contains(needle) || packageName.lowercase().contains(needle)
    }
}
