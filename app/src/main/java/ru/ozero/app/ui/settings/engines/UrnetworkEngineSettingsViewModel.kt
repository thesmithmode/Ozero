package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.FilteredLocations
import com.bringyour.sdk.LocationsViewController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import java.util.Locale
import javax.inject.Inject

data class UrnetworkLocationItem(
    val location: ConnectLocation,
    val name: String,
    val nameRu: String,
    val countryCode: String,
    val flag: String,
    val providerCount: Int,
)

sealed interface UrnetworkSettingsUiState {
    data object Loading : UrnetworkSettingsUiState
    data object NotConnected : UrnetworkSettingsUiState
    data class Ready(
        val countries: List<UrnetworkLocationItem>,
        val selectedLocation: ConnectLocation?,
        val providePaused: Boolean,
    ) : UrnetworkSettingsUiState
}

@HiltViewModel
class UrnetworkEngineSettingsViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UrnetworkSettingsUiState>(UrnetworkSettingsUiState.Loading)
    val uiState: StateFlow<UrnetworkSettingsUiState> = _uiState.asStateFlow()

    val searchQuery = MutableStateFlow("")

    val peerCount: StateFlow<Int> = flow {
        while (true) {
            emit(bridge.peerCount())
            delay(PEER_COUNT_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PEER_COUNT_KEEP_ALIVE_MS), 0)

    private var locationsVc: LocationsViewController? = null
    private var allCountries: List<UrnetworkLocationItem> = emptyList()

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
                vc.addFilteredLocationsListener { filtered, _ ->
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

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        applyFilter(query)
    }

    fun setProvidePaused(paused: Boolean) {
        bridge.setProvidePaused(paused)
        val current = _uiState.value
        if (current is UrnetworkSettingsUiState.Ready) {
            _uiState.value = current.copy(providePaused = paused)
        }
    }

    private fun updateLocations(filtered: FilteredLocations?) {
        if (filtered == null) return
        allCountries = buildList {
            val list = filtered.countries ?: return@buildList
            for (i in 0 until list.len()) {
                val loc = list.get(i) ?: continue
                val code = loc.countryCode ?: ""
                add(
                    UrnetworkLocationItem(
                        location = loc,
                        name = loc.name ?: loc.country ?: "Unknown",
                        nameRu = if (code.length == 2) {
                            Locale("", code).getDisplayCountry(Locale("ru"))
                        } else {
                            ""
                        },
                        countryCode = code,
                        flag = countryCodeToFlag(code),
                        providerCount = loc.providerCount,
                    ),
                )
            }
        }
        applyFilter(searchQuery.value)
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allCountries
        } else {
            allCountries.filter { item ->
                item.name.lowercase().contains(q) ||
                    item.nameRu.lowercase().contains(q) ||
                    item.countryCode.lowercase().contains(q)
            }
        }
        val current = _uiState.value
        val selectedLocation = if (current is UrnetworkSettingsUiState.Ready) {
            current.selectedLocation
        } else {
            bridge.selectedLocation()
        }
        val providePaused = if (current is UrnetworkSettingsUiState.Ready) {
            current.providePaused
        } else {
            bridge.isProvidePaused()
        }
        _uiState.value = UrnetworkSettingsUiState.Ready(
            countries = filtered,
            selectedLocation = selectedLocation,
            providePaused = providePaused,
        )
    }

    companion object {
        private const val PEER_COUNT_POLL_MS = 2_000L
        private const val PEER_COUNT_KEEP_ALIVE_MS = 5_000L

        fun countryCodeToFlag(code: String): String {
            if (code.length != 2) return ""
            val first = code[0].uppercaseChar().code - 'A'.code + 0x1F1E6
            val second = code[1].uppercaseChar().code - 'A'.code + 0x1F1E6
            return String(intArrayOf(first, second), 0, 2)
        }
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
