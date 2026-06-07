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
import ru.ozero.engineurnetwork.UrnetworkCachedLocation
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.byClientJwt
import ru.ozero.engineurnetwork.setProvideEnabled
import ru.ozero.engineurnetwork.setCachedLocations
import ru.ozero.engineurnetwork.setSelectedLocation
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
    private var storedSelection: UrnetworkLocationSelection = UrnetworkLocationSelection.EMPTY

    private val bootstrapJob: Job

    init {
        viewModelScope.launch {
            configStore.config().collectLatest { cfg ->
                storedSelection = cfg.selectedLocation
                allCountries = cfg.cachedCountries.map { it.toLocationItem() }
                allRegions = cfg.cachedRegions.map { it.toLocationItem() }
                allCities = cfg.cachedCities.map { it.toLocationItem() }
                allBestMatches = cfg.cachedBestMatches.map { it.toLocationItem() }
                val hasBootstrapJwt = cfg.byClientJwt?.isNotBlank() == true
                if (hasCachedLocations()) {
                    _uiState.update { current ->
                        when (current) {
                            UrnetworkSettingsUiState.Loading -> buildCachedReady()
                            is UrnetworkSettingsUiState.Ready -> current.copy(
                                countries = allCountries,
                                regions = allRegions,
                                cities = allCities,
                                bestMatches = if (searchQuery.value.isBlank()) emptyList() else allBestMatches,
                                selectedLocation = selectedLocationForUi(),
                            )
                            is UrnetworkSettingsUiState.NotConnected -> buildCachedReady()
                        }
                    }
                } else {
                    _uiState.update { current ->
                        when (current) {
                            UrnetworkSettingsUiState.Loading -> if (hasBootstrapJwt) current else UrnetworkSettingsUiState.NotConnected
                            is UrnetworkSettingsUiState.Ready -> UrnetworkSettingsUiState.NotConnected
                            UrnetworkSettingsUiState.NotConnected -> current
                        }
                    }
                }
            }
        }
        bootstrapJob = viewModelScope.launch {
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
                bootstrapJob.join()
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
                        if (hasCachedLocations()) {
                            _uiState.update { current ->
                                if (current is UrnetworkSettingsUiState.Ready) {
                                    current.copy(providePaused = true)
                                } else {
                                    buildCachedReady()
                                }
                            }
                        } else {
                            _uiState.value = UrnetworkSettingsUiState.NotConnected
                        }
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
        val targetCountry = location?.countryCode
        val targetSelection = UrnetworkLocationSelection(
            countryCode = targetCountry,
            region = location?.region,
            city = location?.city,
        )
        storedSelection = targetSelection
        if (isUrnetworkActive.value) {
            val previousCountry = bridge.selectedLocation()?.countryCode
            when (location) {
                null -> bridge.connectBestAvailable()
                is SdkLocationToken -> bridge.connectTo(location)
                else -> {
                    bridge.setPreferredLocation(targetSelection.normalized())
                    bridge.connectPreferredLocation()
                }
            }
            if (previousCountry != targetCountry) startSwitchingIndicator()
        }
        viewModelScope.launch {
            runCatching { settingsRepository.setUrnetworkCountryCode(targetCountry) }
            runCatching { configStore.setSelectedLocation(targetSelection) }
        }
        runCatching { bridge.setPreferredLocation(targetSelection.normalized()) }
        _uiState.update { current ->
            when (current) {
                is UrnetworkSettingsUiState.Ready -> current.copy(selectedLocation = location)
                is UrnetworkSettingsUiState.Loading,
                is UrnetworkSettingsUiState.NotConnected -> readyState(
                    countries = allCountries,
                    regions = allRegions,
                    cities = allCities,
                    bestMatches = if (searchQuery.value.isBlank()) emptyList() else allBestMatches,
                    selectedLocation = location,
                )
            }
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
            when (current) {
                is UrnetworkSettingsUiState.Ready -> current.copy(providePaused = paused)
                is UrnetworkSettingsUiState.Loading,
                is UrnetworkSettingsUiState.NotConnected -> readyState(
                    countries = allCountries,
                    regions = allRegions,
                    cities = allCities,
                    bestMatches = if (searchQuery.value.isBlank()) emptyList() else allBestMatches,
                    providePaused = paused,
                )
            }
        }
    }

    private fun updateLocations(filtered: FilteredLocations?) {
        if (filtered == null) return
        allCountries = filtered.countries.toLocationItems()
        allRegions = filtered.regions.toLocationItems()
        allCities = filtered.cities.toLocationItems()
        allBestMatches = filtered.bestMatches.toLocationItems()
        viewModelScope.launch {
            runCatching {
                configStore.setCachedLocations(
                    countries = allCountries.map { it.toCachedLocation() },
                    regions = allRegions.map { it.toCachedLocation() },
                    cities = allCities.map { it.toCachedLocation() },
                    bestMatches = allBestMatches.map { it.toCachedLocation() },
                )
            }
        }
        Log.i(
            TAG,
            "updateLocations: countries=${allCountries.size} regions=${allRegions.size} " +
                "cities=${allCities.size} bestMatches=${allBestMatches.size} query='${searchQuery.value}'",
        )
        applyFilter(searchQuery.value)
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
                selectedLocationForUi()
            } else {
                selectedLocationFromStore()
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

    private fun hasCachedLocations(): Boolean =
        allCountries.isNotEmpty() || allRegions.isNotEmpty() || allCities.isNotEmpty() || allBestMatches.isNotEmpty()

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
        readyState(
            countries = emptyList(),
            regions = emptyList(),
            cities = emptyList(),
            bestMatches = emptyList(),
        )

    private fun buildCachedReady(): UrnetworkSettingsUiState.Ready =
        readyState(
            countries = allCountries,
            regions = allRegions,
            cities = allCities,
            bestMatches = if (searchQuery.value.isBlank()) emptyList() else allBestMatches,
            providePaused = true,
        )

    private fun readyState(
        countries: List<UrnetworkLocationItem>,
        regions: List<UrnetworkLocationItem>,
        cities: List<UrnetworkLocationItem>,
        bestMatches: List<UrnetworkLocationItem>,
        selectedLocation: UrnetworkSdkBridge.LocationToken? = null,
        providePaused: Boolean? = null,
    ): UrnetworkSettingsUiState.Ready = UrnetworkSettingsUiState.Ready(
        countries = countries,
        regions = regions,
        cities = cities,
        bestMatches = bestMatches,
        selectedLocation = selectedLocation ?: selectedLocationForUi(),
        providePaused = providePaused ?: if (isUrnetworkActive.value) bridge.isProvidePaused() else false,
    )

    private fun teardownLocationsVc() {
        locationsVc?.also {
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        locationsVc = null
    }

    private fun selectedLocationForUi(): UrnetworkSdkBridge.LocationToken? {
        val sdkSelected = bridge.selectedLocation()
        if (sdkSelected != null && !sdkSelected.bestAvailable) return sdkSelected
        return selectedLocationFromStore()
    }

    private fun selectedLocationFromStore(): UrnetworkSdkBridge.LocationToken? {
        val selection = storedSelection.normalized() ?: return null
        return findStoredLocation(selection) ?: UrnetworkCachedLocation(
            name = selection.summary(),
            countryCode = selection.countryCode,
            region = selection.region,
            city = selection.city,
        )
    }

    private fun findStoredLocation(selection: UrnetworkLocationSelection): UrnetworkSdkBridge.LocationToken? {
        val normalized = selection.normalized() ?: return null
        return (allBestMatches + allCountries + allRegions + allCities)
            .firstOrNull { item ->
                item.location.countryCode?.equals(normalized.countryCode, ignoreCase = true) == true &&
                    item.location.region == normalized.region &&
                    item.location.city == normalized.city
            }
            ?.location
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
    }
}

private fun com.bringyour.sdk.ConnectLocationList?.toLocationItems(): List<UrnetworkLocationItem> =
    buildList {
        val list = this@toLocationItems ?: return@buildList
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

private fun UrnetworkCachedLocation.toLocationItem(): UrnetworkLocationItem =
    UrnetworkLocationItem(
        location = this,
        name = name,
        nameRu = Locale("", countryCode.orEmpty()).getDisplayCountry(Locale("ru")),
        countryCode = countryCode.orEmpty(),
        flag = countryCodeToFlag(countryCode.orEmpty()),
        providerCount = providerCount,
        isStable = isStable,
        isStrongPrivacy = isStrongPrivacy,
    )

private fun UrnetworkLocationItem.toCachedLocation(): UrnetworkCachedLocation =
    UrnetworkCachedLocation(
        name = name,
        countryCode = countryCode,
        region = location.region,
        city = location.city,
        providerCount = providerCount,
        isStable = isStable,
        isStrongPrivacy = isStrongPrivacy,
    )

private fun countryCodeToFlag(code: String): String {
    if (code.length != 2) return ""
    val first = code[0].uppercaseChar().code - 'A'.code + 0x1F1E6
    val second = code[1].uppercaseChar().code - 'A'.code + 0x1F1E6
    return String(intArrayOf(first, second), 0, 2)
}
