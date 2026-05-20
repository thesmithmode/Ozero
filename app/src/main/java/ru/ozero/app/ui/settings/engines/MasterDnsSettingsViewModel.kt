package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginemasterdns.MasterDnsConfigStore
import ru.ozero.enginescore.PersistentLoggers
import javax.inject.Inject

data class MasterDnsSettingsState(
    val enabled: Boolean = false,
    val configToml: String = "",
    val resolversText: String = "",
)

@HiltViewModel
class MasterDnsSettingsViewModel @Inject constructor(
    private val store: MasterDnsConfigStore,
) : ViewModel() {

    val state: StateFlow<MasterDnsSettingsState> = store.config()
        .map { cfg ->
            MasterDnsSettingsState(
                enabled = cfg.enabled,
                configToml = cfg.configToml,
                resolversText = cfg.resolvers.joinToString("\n"),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MasterDnsSettingsState())

    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { store.setEnabled(enabled) }
                .onFailure { PersistentLoggers.error(TAG, "setEnabled failed: ${it.message}", it) }
        }
    }

    fun onConfigTomlChange(toml: String) {
        viewModelScope.launch {
            runCatching { store.setConfigToml(toml) }
                .onFailure { PersistentLoggers.error(TAG, "setConfigToml failed: ${it.message}", it) }
        }
    }

    fun onResolversChange(text: String) {
        val list = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            runCatching { store.setResolvers(list) }
                .onFailure { PersistentLoggers.error(TAG, "setResolvers failed: ${it.message}", it) }
        }
    }

    private companion object {
        const val TAG = "MasterDnsSettingsVM"
    }
}
