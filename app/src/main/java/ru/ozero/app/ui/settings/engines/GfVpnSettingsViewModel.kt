package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.settings.GfVpnOptions
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class GfVpnSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val tunnelController: TunnelController,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsModel.DEFAULT)
    val editable = tunnelController.state.map { it is TunnelState.Idle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun updateOptions(transform: (GfVpnOptions) -> GfVpnOptions) {
        viewModelScope.launch {
            if (tunnelController.state.value is TunnelState.Idle) repository.updateGfVpnOptions(transform)
        }
    }
}
