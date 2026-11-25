package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class OperaEngineSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsModel.DEFAULT)

    fun setAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setOperaAutoEnabled(enabled) }
    }

    fun selectRegion(region: String) {
        viewModelScope.launch { repository.setOperaRegion(region) }
    }

    fun selectServer(selection: String) {
        viewModelScope.launch { repository.setOperaSelection(selection) }
    }
}
