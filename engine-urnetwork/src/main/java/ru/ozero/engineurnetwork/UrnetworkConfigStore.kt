package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

interface UrnetworkConfigStore {
    fun config(): Flow<UrnetworkConfig>
    suspend fun update(transform: (UrnetworkConfig) -> UrnetworkConfig)
}

data class UrnetworkConfig(
    val walletOverride: String? = null,
    val byJwt: String? = null,
    val byClientJwt: String? = null,
    val devicePubkey: String? = null,
    val deviceNetworkName: String? = null,
    val windowType: UrnetworkWindowType = UrnetworkWindowType.AUTO,
    val fixedIpSize: Boolean = false,
    val allowDirect: Boolean = true,
    val provideEnabled: Boolean = true,
    val provideControlMode: UrnetworkProvideControlMode = UrnetworkProvideControlMode.ALWAYS,
    val provideNetworkMode: UrnetworkProvideNetworkMode = UrnetworkProvideNetworkMode.WIFI,
    val selectedLocation: UrnetworkLocationSelection = UrnetworkLocationSelection.EMPTY,
    val cachedCountries: List<UrnetworkCachedLocation> = emptyList(),
    val cachedRegions: List<UrnetworkCachedLocation> = emptyList(),
    val cachedCities: List<UrnetworkCachedLocation> = emptyList(),
    val cachedBestMatches: List<UrnetworkCachedLocation> = emptyList(),
) {
    val walletAddress: String
        get() = walletOverride?.takeIf { it.isNotBlank() } ?: UrnetworkDefaults.PRESET_WALLET
}

data class UrnetworkCachedLocation(
    val name: String,
    override val countryCode: String?,
    override val region: String? = null,
    override val city: String? = null,
    val providerCount: Int = 0,
    val isStable: Boolean = true,
    val isStrongPrivacy: Boolean = false,
) : UrnetworkSdkBridge.LocationToken

data class UrnetworkLocationSelection(
    val countryCode: String?,
    val region: String?,
    val city: String?,
) {
    fun normalized(): UrnetworkLocationSelection? {
        val cc = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch.isLetter() } }
        val r = region?.trim()?.takeIf { it.isNotEmpty() }
        val c = city?.trim()?.takeIf { it.isNotEmpty() }
        if (cc == null && r == null && c == null) return null
        return UrnetworkLocationSelection(cc, r, c)
    }

    fun summary(): String = buildString {
        append(countryCode ?: "??")
        if (!region.isNullOrBlank()) append('/').append(region)
        if (!city.isNullOrBlank()) append('/').append(city)
    }

    companion object {
        val EMPTY = UrnetworkLocationSelection(null, null, null)
    }
}

fun UrnetworkConfigStore.walletAddress(): Flow<String> = config().map { it.walletAddress }

fun UrnetworkConfigStore.walletOverride(): Flow<String?> =
    config().map { it.walletOverride?.takeIf { v -> v.isNotBlank() } }

suspend fun UrnetworkConfigStore.setWalletOverride(value: String?) {
    update { it.copy(walletOverride = value?.takeIf { v -> v.isNotBlank() }) }
}

fun UrnetworkConfigStore.byJwt(): Flow<String?> =
    config().map { it.byJwt?.takeIf { v -> v.isNotBlank() } }

suspend fun UrnetworkConfigStore.setByJwt(value: String?) {
    update { it.copy(byJwt = value?.takeIf { v -> v.isNotBlank() }) }
}

fun UrnetworkConfigStore.byClientJwt(): Flow<String?> =
    config().map { it.byClientJwt?.takeIf { v -> v.isNotBlank() } }

suspend fun UrnetworkConfigStore.setByClientJwt(value: String?) {
    update { it.copy(byClientJwt = value?.takeIf { v -> v.isNotBlank() }) }
}

fun UrnetworkConfigStore.devicePubkey(): Flow<String?> =
    config().map { it.devicePubkey?.takeIf { v -> v.isNotBlank() } }

suspend fun UrnetworkConfigStore.setDevicePubkey(value: String?) {
    update { it.copy(devicePubkey = value?.takeIf { v -> v.isNotBlank() }) }
}

fun UrnetworkConfigStore.deviceNetworkName(): Flow<String?> =
    config().map { it.deviceNetworkName?.takeIf { v -> v.isNotBlank() } }

suspend fun UrnetworkConfigStore.setDeviceNetworkName(value: String?) {
    update { it.copy(deviceNetworkName = value?.takeIf { v -> v.isNotBlank() }) }
}

fun UrnetworkConfigStore.windowType(): Flow<UrnetworkWindowType> = config().map { it.windowType }

suspend fun UrnetworkConfigStore.setWindowType(value: UrnetworkWindowType) {
    update { it.copy(windowType = value) }
}

fun UrnetworkConfigStore.fixedIpSize(): Flow<Boolean> = config().map { it.fixedIpSize }

suspend fun UrnetworkConfigStore.setFixedIpSize(value: Boolean) {
    update { it.copy(fixedIpSize = value) }
}

fun UrnetworkConfigStore.allowDirect(): Flow<Boolean> = config().map { it.allowDirect }

suspend fun UrnetworkConfigStore.setAllowDirect(value: Boolean) {
    update { it.copy(allowDirect = value) }
}

fun UrnetworkConfigStore.provideNetworkMode(): Flow<UrnetworkProvideNetworkMode> =
    config().map { it.provideNetworkMode }

suspend fun UrnetworkConfigStore.setProvideNetworkMode(value: UrnetworkProvideNetworkMode) {
    update { it.copy(provideNetworkMode = value) }
}

fun UrnetworkConfigStore.selectedLocation(): Flow<UrnetworkLocationSelection> =
    config().map { it.selectedLocation }

suspend fun UrnetworkConfigStore.setSelectedLocation(value: UrnetworkLocationSelection) {
    update { current ->
        current.copy(
            selectedLocation = UrnetworkLocationSelection(
                countryCode = value.countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                region = value.region?.trim()?.takeIf { it.isNotEmpty() },
                city = value.city?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}

suspend fun UrnetworkConfigStore.setCachedLocations(
    countries: List<UrnetworkCachedLocation>,
    regions: List<UrnetworkCachedLocation>,
    cities: List<UrnetworkCachedLocation>,
    bestMatches: List<UrnetworkCachedLocation>,
) {
    update {
        it.copy(
            cachedCountries = countries,
            cachedRegions = regions,
            cachedCities = cities,
            cachedBestMatches = bestMatches,
        )
    }
}

class InMemoryUrnetworkConfigStore(initial: UrnetworkConfig = UrnetworkConfig()) : UrnetworkConfigStore {
    private val state = MutableStateFlow(initial.normalizedProvideState())
    val snapshot: UrnetworkConfig get() = state.value
    fun inject(transform: (UrnetworkConfig) -> UrnetworkConfig) {
        state.value = transform(state.value).normalizedProvideState()
    }
    override fun config(): Flow<UrnetworkConfig> = state
    override suspend fun update(transform: (UrnetworkConfig) -> UrnetworkConfig) {
        state.value = transform(state.value).normalizedProvideState()
    }

    private fun UrnetworkConfig.normalizedProvideState(): UrnetworkConfig = copy(
        provideEnabled = true,
        provideControlMode = UrnetworkProvideControlMode.ALWAYS,
    )
}
