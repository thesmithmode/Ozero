package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class DataStoreUrnetworkConfigStore(
    private val dataStore: DataStore<Preferences>,
) : UrnetworkConfigStore {
    private val latest = MutableStateFlow(UrnetworkConfig())

    override fun config(): Flow<UrnetworkConfig> = latest.asStateFlow()

    override suspend fun update(transform: (UrnetworkConfig) -> UrnetworkConfig) {
        dataStore.edit { prefs ->
            val next = transform(readConfig(prefs))
            writeConfig(prefs, next)
            latest.value = next.withNormalizedCachedLocations()
        }
    }

    private fun readConfig(prefs: Preferences): UrnetworkConfig = UrnetworkConfig(
        walletOverride = prefs[KEY_WALLET_OVERRIDE]?.takeIf { it.isNotBlank() },
        byJwt = prefs[KEY_BY_JWT]?.takeIf { it.isNotBlank() },
        byClientJwt = prefs[KEY_BY_CLIENT_JWT]?.takeIf { it.isNotBlank() },
        devicePubkey = prefs[KEY_DEVICE_PUBKEY]?.takeIf { it.isNotBlank() },
        deviceNetworkName = prefs[KEY_DEVICE_NETWORK_NAME]?.takeIf { it.isNotBlank() },
        windowType = UrnetworkWindowType.fromRaw(prefs[KEY_WINDOW_TYPE]),
        fixedIpSize = prefs[KEY_FIXED_IP_SIZE] == true,
        allowDirect = prefs[KEY_ALLOW_DIRECT] != false,
        provideEnabled = prefs[KEY_PROVIDE_ENABLED] != false,
        provideControlMode = UrnetworkProvideControlMode.fromRaw(prefs[KEY_PROVIDE_CONTROL_MODE]),
        provideNetworkMode = UrnetworkProvideNetworkMode.fromRaw(prefs[KEY_PROVIDE_NETWORK_MODE]),
        selectedLocation = UrnetworkLocationSelection(
            countryCode = prefs[KEY_SELECTED_COUNTRY_CODE]?.takeIf { it.isNotBlank() },
            region = prefs[KEY_SELECTED_REGION]?.takeIf { it.isNotBlank() },
            city = prefs[KEY_SELECTED_CITY]?.takeIf { it.isNotBlank() },
        ),
        cachedCountries = readLocationCache(prefs[KEY_CACHED_COUNTRIES]),
        cachedRegions = readLocationCache(prefs[KEY_CACHED_REGIONS]),
        cachedCities = readLocationCache(prefs[KEY_CACHED_CITIES]),
        cachedBestMatches = readLocationCache(prefs[KEY_CACHED_BEST_MATCHES]),
    )

    private fun writeConfig(prefs: MutablePreferences, cfg: UrnetworkConfig) {
        prefs.writeOrRemove(KEY_WALLET_OVERRIDE, cfg.walletOverride)
        prefs.writeOrRemove(KEY_BY_JWT, cfg.byJwt)
        prefs.writeOrRemove(KEY_BY_CLIENT_JWT, cfg.byClientJwt)
        prefs.writeOrRemove(KEY_DEVICE_PUBKEY, cfg.devicePubkey)
        prefs.writeOrRemove(KEY_DEVICE_NETWORK_NAME, cfg.deviceNetworkName)
        prefs[KEY_WINDOW_TYPE] = cfg.windowType.rawValue
        prefs[KEY_FIXED_IP_SIZE] = cfg.fixedIpSize
        prefs[KEY_ALLOW_DIRECT] = cfg.allowDirect
        prefs[KEY_PROVIDE_ENABLED] = cfg.provideEnabled
        prefs[KEY_PROVIDE_CONTROL_MODE] = cfg.provideControlMode.rawValue
        prefs[KEY_PROVIDE_NETWORK_MODE] = cfg.provideNetworkMode.rawValue
        prefs.writeOrRemove(KEY_SELECTED_COUNTRY_CODE, cfg.selectedLocation.countryCode)
        prefs.writeOrRemove(KEY_SELECTED_REGION, cfg.selectedLocation.region)
        prefs.writeOrRemove(KEY_SELECTED_CITY, cfg.selectedLocation.city)
        prefs.writeOrRemove(KEY_CACHED_COUNTRIES, writeLocationCache(cfg.cachedCountries))
        prefs.writeOrRemove(KEY_CACHED_REGIONS, writeLocationCache(cfg.cachedRegions))
        prefs.writeOrRemove(KEY_CACHED_CITIES, writeLocationCache(cfg.cachedCities))
        prefs.writeOrRemove(KEY_CACHED_BEST_MATCHES, writeLocationCache(cfg.cachedBestMatches))
    }

    private fun readLocationCache(raw: String?): List<UrnetworkCachedLocation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val code = obj.nullableString("code")?.takeIf { it.length == 2 } ?: continue
                    add(
                        UrnetworkCachedLocation(
                            name = name,
                            countryCode = code.uppercase(),
                            region = obj.nullableString("region"),
                            city = obj.nullableString("city"),
                            providerCount = obj.optInt("providers", 0),
                            isStable = obj.optBoolean("stable", true),
                            isStrongPrivacy = obj.optBoolean("privacy", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeLocationCache(items: List<UrnetworkCachedLocation>): String? {
        if (items.isEmpty()) return null
        val arr = JSONArray()
        items.take(MAX_CACHED_LOCATIONS).forEach { loc ->
            arr.put(
                JSONObject().apply {
                    put("name", loc.name)
                    put("code", loc.countryCode)
                    loc.region?.takeIf { it.isNotBlank() }?.let { put("region", it) }
                    loc.city?.takeIf { it.isNotBlank() }?.let { put("city", it) }
                    put("providers", loc.providerCount)
                    put("stable", loc.isStable)
                    put("privacy", loc.isStrongPrivacy)
                },
            )
        }
        return arr.toString()
    }

    private fun UrnetworkConfig.withNormalizedCachedLocations(): UrnetworkConfig =
        copy(
            cachedCountries = cachedCountries.map {
                it.copy(countryCode = it.countryCode?.uppercase())
            }.take(MAX_CACHED_LOCATIONS),
            cachedRegions = cachedRegions.map {
                it.copy(countryCode = it.countryCode?.uppercase())
            }.take(MAX_CACHED_LOCATIONS),
            cachedCities = cachedCities.map {
                it.copy(countryCode = it.countryCode?.uppercase())
            }.take(MAX_CACHED_LOCATIONS),
            cachedBestMatches = cachedBestMatches.map {
                it.copy(countryCode = it.countryCode?.uppercase())
            }.take(MAX_CACHED_LOCATIONS),
        )

    private companion object {
        val KEY_WALLET_OVERRIDE = stringPreferencesKey("urnetwork_wallet_override")
        val KEY_BY_JWT = stringPreferencesKey("urnetwork_by_jwt")
        val KEY_BY_CLIENT_JWT = stringPreferencesKey("urnetwork_by_client_jwt")
        val KEY_DEVICE_PUBKEY = stringPreferencesKey("urnetwork_device_pubkey")
        val KEY_DEVICE_NETWORK_NAME = stringPreferencesKey("urnetwork_device_network_name")
        val KEY_WINDOW_TYPE = stringPreferencesKey("urnetwork_window_type")
        val KEY_FIXED_IP_SIZE = booleanPreferencesKey("urnetwork_fixed_ip_size")
        val KEY_ALLOW_DIRECT = booleanPreferencesKey("urnetwork_allow_direct")
        val KEY_PROVIDE_ENABLED = booleanPreferencesKey("urnetwork_provide_enabled")
        val KEY_PROVIDE_CONTROL_MODE = stringPreferencesKey("urnetwork_provide_control_mode")
        val KEY_PROVIDE_NETWORK_MODE = stringPreferencesKey("urnetwork_provide_network_mode")
        val KEY_SELECTED_COUNTRY_CODE = stringPreferencesKey("urnetwork_selected_country_code")
        val KEY_SELECTED_REGION = stringPreferencesKey("urnetwork_selected_region")
        val KEY_SELECTED_CITY = stringPreferencesKey("urnetwork_selected_city")
        val KEY_CACHED_COUNTRIES = stringPreferencesKey("urnetwork_cached_countries")
        val KEY_CACHED_REGIONS = stringPreferencesKey("urnetwork_cached_regions")
        val KEY_CACHED_CITIES = stringPreferencesKey("urnetwork_cached_cities")
        val KEY_CACHED_BEST_MATCHES = stringPreferencesKey("urnetwork_cached_best_matches")
        const val MAX_CACHED_LOCATIONS = 500
    }
}

private fun MutablePreferences.writeOrRemove(key: Preferences.Key<String>, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let { this[key] = it } ?: remove(key)
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
