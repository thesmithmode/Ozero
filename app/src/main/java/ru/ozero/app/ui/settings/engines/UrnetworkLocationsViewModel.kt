package ru.ozero.app.ui.settings.engines

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.FilteredLocations
import com.bringyour.sdk.LocationsViewController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.engineurnetwork.SdkLocationToken
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.byClientJwt
import ru.ozero.engineurnetwork.setProvideEnabled
import ru.ozero.engineurnetwork.walletAddress
import java.util.Locale
import javax.inject.Inject

data class UrnetworkLocationItem(
    val location: UrnetworkSdkBridge.LocationToken,
    val name: String,
    val nameRu: String,
    val countryCode: String,
    val flag: String,
    val providerCount: Int,
    val isStable: Boolean = true,
    val isStrongPrivacy: Boolean = false,
)

sealed interface UrnetworkSettingsUiState {
    data object Loading : UrnetworkSettingsUiState
    data object NotConnected : UrnetworkSettingsUiState
    data class Ready(
        val countries: List<UrnetworkLocationItem>,
        val regions: List<UrnetworkLocationItem>,
        val cities: List<UrnetworkLocationItem>,
        val bestMatches: List<UrnetworkLocationItem>,
        val selectedLocation: UrnetworkSdkBridge.LocationToken?,
        val providePaused: Boolean,
    ) : UrnetworkSettingsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UrnetworkLocationsViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
    private val settingsRepository: SettingsRepository,
    private val configStore: UrnetworkConfigStore,
    private val tunnelController: TunnelController,
) : ViewModel() {

    val isUrnetworkActive: StateFlow<Boolean> = tunnelController.state
        .map { s ->
            when (s) {
                is TunnelState.Connecting -> s.engineId == EngineId.URNETWORK
                is TunnelState.Connected -> s.engineId == EngineId.URNETWORK
                else -> false
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _uiState = MutableStateFlow<UrnetworkSettingsUiState>(UrnetworkSettingsUiState.Loading)
    val uiState: StateFlow<UrnetworkSettingsUiState> = _uiState.asStateFlow()

    private val _switchingCountry = MutableStateFlow(false)
    val switchingCountry: StateFlow<Boolean> = _switchingCountry.asStateFlow()

    val searchQuery = MutableStateFlow("")

    @Volatile private var locationsVc: LocationsViewController? = null
    private var switchingJob: Job? = null
    private var allCountries: List<UrnetworkLocationItem> = emptyList()
    private var allRegions: List<UrnetworkLocationItem> = emptyList()
    private var allCities: List<UrnetworkLocationItem> = emptyList()
    private var allBestMatches: List<UrnetworkLocationItem> = emptyList()

    init {
        viewModelScope.launch {
            val byClientJwt = configStore.byClientJwt().first()
            val wallet = configStore.walletAddress().first()
            if (!byClientJwt.isNullOrBlank()) {
                val ready = bridge.initDeviceForLocations(byClientJwt, wallet)
                if (ready && _uiState.value !is UrnetworkSettingsUiState.Ready) {
                    refreshOnce()
                }
            }
        }
        viewModelScope.launch {
            isUrnetworkActive.collectLatest { active ->
                if (active) {
                    var attempt = 0
                    while (attempt < REFRESH_RETRY_ATTEMPTS && !isReadyWithLocations()) {
                        if (!isUrnetworkActive.value) break
                        refreshOnce()
                        if (isReadyWithLocations()) break
                        attempt++
                        if (attempt < REFRESH_RETRY_ATTEMPTS) delay(REFRESH_RETRY_DELAY_MS)
                    }
                } else {
                    if (bridge.isDeviceAvailable()) {
                        _uiState.update { current ->
                            if (current is UrnetworkSettingsUiState.Ready) {
                                current.copy(providePaused = true)
                            } else {
                                current
                            }
                        }
                    } else {
                        teardownLocationsVc()
                        allCountries = emptyList()
                        allRegions = emptyList()
                        allCities = emptyList()
                        allBestMatches = emptyList()
                        _uiState.value = UrnetworkSettingsUiState.NotConnected
                    }
                }
            }
        }
    }

    fun refresh() {
        if (isDeviceUnavailable()) {
            _uiState.value = UrnetworkSettingsUiState.NotConnected
            return
        }
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        if (isDeviceUnavailable()) {
            _uiState.value = UrnetworkSettingsUiState.NotConnected
            return
        }
        Log.i(TAG, "refresh: openLocationsViewController")
        val vc = withContext(Dispatchers.Main.immediate) {
            runCatching { bridge.openLocationsViewController() }
                .getOrElse { t ->
                    PersistentLoggers.warn(TAG, "openLocationsViewController threw: ${t.message}")
                    null
                }
        }
        if (vc == null) {
            PersistentLoggers.warn(
                TAG,
                "refresh: openLocationsViewController вернул null — bridge не подключён или SDK не готов",
            )
            handleNullVcFallback()
            return
        }
        teardownLocationsVc()
        locationsVc = vc
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                vc.addFilteredLocationsListener { filtered, _ ->
                    viewModelScope.launch {
                        updateLocations(filtered)
                    }
                }
                vc.start()
                vc.filterLocations("")
                Log.i(TAG, "refresh: locationsVc started, listener attached, initial filterLocations(\"\") triggered")
            }.onFailure {
                PersistentLoggers.warn(TAG, "refresh: locationsVc setup threw: ${it.message}")
                _uiState.value = UrnetworkSettingsUiState.NotConnected
            }
        }
    }

    fun selectLocation(location: UrnetworkSdkBridge.LocationToken?) {
        if (!isUrnetworkActive.value) {
            PersistentLoggers.warn(TAG, "selectLocation skipped — URnetwork engine not active")
            return
        }
        val previousCountry = bridge.selectedLocation()?.countryCode
        val targetCountry = location?.countryCode
        if (location == null) {
            bridge.connectBestAvailable()
        } else {
            bridge.connectTo(location)
        }
        viewModelScope.launch {
            runCatching { settingsRepository.setUrnetworkCountryCode(targetCountry) }
        }
        runCatching {
            bridge.setPreferredLocation(
                UrnetworkLocationSelection(
                    countryCode = targetCountry,
                    region = location?.region,
                    city = location?.city,
                ).normalized(),
            )
        }
        _uiState.update { current ->
            if (current is UrnetworkSettingsUiState.Ready) current.copy(selectedLocation = location) else current
        }
        if (previousCountry != targetCountry) {
            startSwitchingIndicator()
        }
    }

    private fun startSwitchingIndicator() {
        switchingJob?.cancel()
        _switchingCountry.value = true
        switchingJob = viewModelScope.launch {
            var elapsed = 0L
            while (elapsed < SWITCHING_INDICATOR_MAX_MS) {
                if (!isUrnetworkActive.value) break
                if (bridge.peerCount() > 0) break
                delay(SWITCHING_INDICATOR_POLL_MS)
                elapsed += SWITCHING_INDICATOR_POLL_MS
            }
            delay(SWITCHING_INDICATOR_SETTLE_MS)
            _switchingCountry.value = false
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        applyFilter(query)
        val vc = locationsVc
        if (vc != null) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                runCatching { vc.filterLocations(query) }
                    .onFailure { PersistentLoggers.warn(TAG, "filterLocations($query) threw: ${it.message}") }
            }
        }
    }

    fun setProvidePaused(paused: Boolean) {
        if (isUrnetworkActive.value) {
            bridge.setProvidePaused(paused)
        }
        viewModelScope.launch { configStore.setProvideEnabled(!paused) }
        _uiState.update { current ->
            if (current is UrnetworkSettingsUiState.Ready) current.copy(providePaused = paused) else current
        }
    }

    private fun updateLocations(filtered: FilteredLocations?) {
        if (filtered == null) return
        allCountries = buildLocationList(filtered.countries)
        allRegions = buildLocationList(filtered.regions)
        allCities = buildLocationList(filtered.cities)
        allBestMatches = buildLocationList(filtered.bestMatches)
        applyFilter(searchQuery.value)
    }

    private fun buildLocationList(list: com.bringyour.sdk.ConnectLocationList?): List<UrnetworkLocationItem> =
        buildList {
            if (list == null) return@buildList
            for (i in 0 until list.len()) {
                val loc = list.get(i) ?: continue
                val code = loc.countryCode ?: ""
                add(
                    UrnetworkLocationItem(
                        location = SdkLocationToken(loc),
                        name = loc.name ?: loc.country ?: "Unknown",
                        nameRu = if (code.length == 2) {
                            Locale("", code).getDisplayCountry(Locale("ru"))
                        } else {
                            ""
                        },
                        countryCode = code,
                        flag = countryCodeToFlag(code),
                        providerCount = loc.providerCount,
                        isStable = runCatching { loc.stable }.getOrDefault(true),
                        isStrongPrivacy = runCatching { loc.strongPrivacy }.getOrDefault(false),
                    ),
                )
            }
        }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        fun List<UrnetworkLocationItem>.applyQuery() = if (q.isEmpty()) {
            this
        } else {
            filter { item ->
                item.name.lowercase().contains(q) ||
                    item.nameRu.lowercase().contains(q) ||
                    item.countryCode.lowercase().contains(q)
            }
        }
        val filteredCountries = allCountries.applyQuery()
        val filteredRegions = allRegions.applyQuery()
        val filteredCities = allCities.applyQuery()
        val filteredBestMatches = if (q.isEmpty()) emptyList() else allBestMatches.applyQuery()
        _uiState.update { current ->
            val selectedLocation = if (current is UrnetworkSettingsUiState.Ready) {
                current.selectedLocation
            } else if (bridge.isDeviceAvailable()) {
                bridge.selectedLocation()
            } else {
                null
            }
            val providePaused = if (current is UrnetworkSettingsUiState.Ready) {
                current.providePaused
            } else if (isUrnetworkActive.value) {
                bridge.isProvidePaused()
            } else {
                false
            }
            UrnetworkSettingsUiState.Ready(
                countries = filteredCountries,
                regions = filteredRegions,
                cities = filteredCities,
                bestMatches = filteredBestMatches,
                selectedLocation = selectedLocation,
                providePaused = providePaused,
            )
        }
    }

    private fun isReadyWithLocations(): Boolean {
        val s = _uiState.value
        return s is UrnetworkSettingsUiState.Ready && s.countries.isNotEmpty()
    }

    private fun isDeviceUnavailable(): Boolean = !bridge.isDeviceAvailable() && !bridge.isRunning()

    private fun handleNullVcFallback() {
        if (isDeviceUnavailable()) {
            _uiState.value = UrnetworkSettingsUiState.NotConnected
            return
        }
        _uiState.update { current ->
            if (current is UrnetworkSettingsUiState.Ready) current else buildEmptyReady()
        }
    }

    private fun buildEmptyReady(): UrnetworkSettingsUiState.Ready =
        UrnetworkSettingsUiState.Ready(
            countries = emptyList(),
            regions = emptyList(),
            cities = emptyList(),
            bestMatches = emptyList(),
            selectedLocation = bridge.selectedLocation(),
            providePaused = if (isUrnetworkActive.value) bridge.isProvidePaused() else false,
        )

    private fun teardownLocationsVc() {
        locationsVc?.also {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        locationsVc = null
    }

    override fun onCleared() {
        super.onCleared()
        teardownLocationsVc()
    }

    companion object {
        private const val TAG = "UrnetworkLocationsVM"
        private const val REFRESH_RETRY_ATTEMPTS = 15
        private const val REFRESH_RETRY_DELAY_MS = 2_000L
        private const val SWITCHING_INDICATOR_POLL_MS = 1_000L
        private const val SWITCHING_INDICATOR_MAX_MS = 15_000L
        private const val SWITCHING_INDICATOR_SETTLE_MS = 1_500L

        fun countryCodeToFlag(code: String): String {
            if (code.length != 2) return ""
            val first = code[0].uppercaseChar().code - 'A'.code + 0x1F1E6
            val second = code[1].uppercaseChar().code - 'A'.code + 0x1F1E6
            return String(intArrayOf(first, second), 0, 2)
        }
    }
}
