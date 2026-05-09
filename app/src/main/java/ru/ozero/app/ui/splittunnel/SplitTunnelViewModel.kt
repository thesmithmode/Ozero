package ru.ozero.app.ui.splittunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        viewModelScope.launch { apps.value = appListProvider.loadApps() }
        viewModelScope.launch {
            val current = settingsRepository.settings.map { it.splitMode }.first()
            if (current == SplitTunnelMode.BYPASS_LAN) {
                settingsRepository.setSplitMode(SplitTunnelMode.ALL)
            }
        }
        combine(
            apps.filterNotNull(),
            dao.observeAll(),
            settingsRepository.settings.map { it.splitMode },
            query,
        ) { appsList, rules, persistedMode, q ->
            val mode = if (persistedMode == SplitTunnelMode.BYPASS_LAN) SplitTunnelMode.ALL else persistedMode
            val isBlocklist = mode == SplitTunnelMode.BLOCKLIST
            val included = rules.filter { it.isExcluded == isBlocklist }.map { it.packageName }.toSet()
            val filtered = if (q.isBlank()) appsList else appsList.filter { it.matches(q) }
            val rows = filtered.map { app ->
                AppRow(
                    packageName = app.packageName,
                    label = app.label,
                    isSystem = app.isSystem,
                    included = app.packageName in included,
                    icon = app.icon,
                )
            }.sortedWith(
                compareByDescending<AppRow> { it.included }
                    .thenBy { it.label.lowercase() },
            )
            SplitTunnelUiState.Content(
                mode = mode,
                query = q,
                apps = rows,
                selectedCount = included.size,
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onModeChange(mode: SplitTunnelMode) {
        if (mode == SplitTunnelMode.BYPASS_LAN) return
        viewModelScope.launch { settingsRepository.setSplitMode(mode) }
    }

    fun onToggleApp(packageName: String, checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                val mode = settingsRepository.settings.map { it.splitMode }.first()
                dao.upsert(AppSplitRule(packageName = packageName, isExcluded = mode == SplitTunnelMode.BLOCKLIST))
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

    suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? =
        appListProvider.loadIcon(packageName)

    private fun InstalledApp.matches(q: String): Boolean {
        val needle = q.lowercase()
        return label.lowercase().contains(needle) || packageName.lowercase().contains(needle)
    }
}
