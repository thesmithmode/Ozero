package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.FilteredLocations
import com.bringyour.sdk.LocationsViewController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

data class UrnetworkLocationItem(
    val location: ConnectLocation,
    val name: String,
    val countryCode: String,
    val providerCount: Int,
)

sealed interface UrnetworkSettingsUiState {
    data object Loading : UrnetworkSettingsUiState
    data object NotConnected : UrnetworkSettingsUiState
    data class Ready(
        val countries: List<UrnetworkLocationItem>,
        val selectedLocation: ConnectLocation?,
    ) : UrnetworkSettingsUiState
}

@HiltViewModel
class UrnetworkEngineSettingsViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UrnetworkSettingsUiState>(UrnetworkSettingsUiState.Loading)
    val uiState: StateFlow<UrnetworkSettingsUiState> = _uiState.asStateFlow()

    private var locationsVc: LocationsViewController? = null

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val vc = withContext(Dispatchers.Main.immediate) {
                bridge.openLocationsViewController()
            }
            if (vc == null) {
                _uiState.value = UrnetworkSettingsUiState.NotConnected
                return@launch
            }
            locationsVc?.also {
                runCatching { it.stop() }
                runCatching { it.close() }
            }
            locationsVc = vc
            withContext(Dispatchers.Main.immediate) {
                vc.addFilteredLocationsListener { filtered ->
                    viewModelScope.launch {
                        updateLocations(filtered)
                    }
                }
                vc.start()
                vc.filterLocations("")
            }
        }
    }

    fun selectLocation(location: ConnectLocation?) {
        if (location == null) {
            bridge.connectBestAvailable()
        } else {
            bridge.connectTo(location)
        }
        val current = _uiState.value
        if (current is UrnetworkSettingsUiState.Ready) {
            _uiState.value = current.copy(selectedLocation = location)
        }
    }

    private fun updateLocations(filtered: FilteredLocations?) {
        if (filtered == null) return
        val countries = buildList {
            val list = filtered.countries ?: return@buildList
            for (i in 0 until list.len()) {
                val loc = list.get(i) ?: continue
                add(
                    UrnetworkLocationItem(
                        location = loc,
                        name = loc.name ?: loc.country ?: "Unknown",
                        countryCode = loc.countryCode ?: "",
                        providerCount = loc.providerCount,
                    ),
                )
            }
        }
        _uiState.value = UrnetworkSettingsUiState.Ready(
            countries = countries,
            selectedLocation = bridge.selectedLocation(),
        )
    }

    override fun onCleared() {
        super.onCleared()
        locationsVc?.also {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        locationsVc = null
    }
}
