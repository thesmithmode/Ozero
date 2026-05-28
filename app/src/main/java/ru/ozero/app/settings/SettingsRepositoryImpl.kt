package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.AutoStartGateway
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import javax.inject.Inject

@Suppress("TooManyFunctions")
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val autoStartGateway: AutoStartGateway,
) : SettingsRepository {

    override val settings: Flow<SettingsModel> =
        dataStore.data.map { prefs -> prefs.toSettingsModel() }

    override suspend fun setSplitMode(mode: SplitTunnelMode) {
        dataStore.edit { it[SettingsKeys.SPLIT_MODE] = mode.name }
    }

    override suspend fun setIpv6Enabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.IPV6_ENABLED] = enabled }
    }

    override suspend fun setAutoStart(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.AUTO_START] = enabled }
        autoStartGateway.setAutoStart(enabled)
    }

    override suspend fun setTrafficMode(mode: TrafficMode) {
        dataStore.edit { it[SettingsKeys.TRAFFIC_MODE] = mode.name }
    }

    override suspend fun setManualEngine(engine: EngineId?) {
        dataStore.edit { prefs ->
            if (engine == null) {
                prefs.remove(SettingsKeys.MANUAL_ENGINE)
            } else {
                prefs[SettingsKeys.MANUAL_ENGINE] = engine.name
            }
        }
    }

    override suspend fun setEngineAutoPriority(priority: List<EngineId>) {
        dataStore.edit { prefs ->
            val cleaned = priority.distinct().filter { !it.isStub }
            if (cleaned.isEmpty()) {
                prefs.remove(SettingsKeys.ENGINE_AUTO_PRIORITY)
            } else {
                prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = cleaned.joinToString(",") { it.name }
            }
        }
    }

    override suspend fun setUrnetworkEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.URNETWORK_ENABLED] = enabled }
    }

    override suspend fun setUrnetworkJwt(jwt: String?) {
        dataStore.edit { prefs ->
            if (jwt == null) {
                prefs.remove(SettingsKeys.URNETWORK_JWT)
            } else {
                prefs[SettingsKeys.URNETWORK_JWT] = jwt
            }
        }
    }

    override suspend fun setUrnetworkCountryCode(code: String?) {
        dataStore.edit { prefs ->
            val cleaned = code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch.isLetter() } }
            if (cleaned == null) {
                prefs.remove(SettingsKeys.URNETWORK_COUNTRY_CODE)
            } else {
                prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] = cleaned
            }
        }
    }

    override suspend fun setByedpiWinningArgs(args: String?) {
        dataStore.edit { prefs ->
            if (args.isNullOrBlank()) {
                prefs.remove(SettingsKeys.BYDPI_WINNING_ARGS)
            } else {
                prefs[SettingsKeys.BYDPI_WINNING_ARGS] = args
            }
        }
    }

    override suspend fun setByedpiDefaultAccepted(accepted: Boolean) {
        dataStore.edit { it[SettingsKeys.BYDPI_DEFAULT_ACCEPTED] = accepted }
    }

    override suspend fun setByedpiUseUiMode(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.BYDPI_USE_UI_MODE] = enabled }
    }

    override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) {
        dataStore.edit { it[SettingsKeys.BYDPI_UI_SETTINGS_JSON] = settings.toJson() }
    }

    override suspend fun setCustomDnsServers(servers: List<String>) {
        dataStore.edit { prefs ->
            val cleaned = servers
                .map { it.trim() }
                .filter { it.isNotEmpty() && isValidDnsAddress(it) }
            if (cleaned.isEmpty()) {
                prefs.remove(SettingsKeys.CUSTOM_DNS_SERVERS)
            } else {
                prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = cleaned.joinToString(",")
            }
        }
    }

    override suspend fun setHostsMode(mode: HostsMode) {
        dataStore.edit { it[SettingsKeys.HOSTS_MODE] = mode.name }
    }

    override suspend fun setHosts(hosts: List<String>) {
        dataStore.edit { prefs ->
            val cleaned = hosts
                .map { it.trim() }
                .filter { it.isNotEmpty() && isValidHostname(it) }
            if (cleaned.isEmpty()) {
                prefs.remove(SettingsKeys.HOSTS_LIST)
            } else {
                prefs[SettingsKeys.HOSTS_LIST] = cleaned.joinToString(",")
            }
        }
    }

    override suspend fun setUiLocaleTag(tag: String?) {
        dataStore.edit { prefs ->
            if (tag.isNullOrBlank()) {
                prefs.remove(SettingsKeys.UI_LOCALE_TAG)
            } else {
                prefs[SettingsKeys.UI_LOCALE_TAG] = tag
            }
        }
    }

    override suspend fun setAppMode(mode: AppMode) {
        dataStore.edit { it[SettingsKeys.APP_MODE] = mode.name }
    }

    override suspend fun setKillswitchEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.KILLSWITCH_ENABLED] = enabled }
    }

    override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) {
        dataStore.edit { it[SettingsKeys.ALWAYS_ON_BANNER_DISMISSED] = dismissed }
    }

    private fun Preferences.toSettingsModel(): SettingsModel = SettingsModel(
        splitMode = readSplitMode(),
        ipv6Enabled = this[SettingsKeys.IPV6_ENABLED] ?: SettingsModel.DEFAULT_IPV6_ENABLED,
        autoStart = this[SettingsKeys.AUTO_START] ?: SettingsModel.DEFAULT_AUTO_START,
        trafficMode = readTrafficMode(),
        manualEngine = readManualEngine(),
        engineAutoPriority = readEngineAutoPriority(),
        urnetworkEnabled = this[SettingsKeys.URNETWORK_ENABLED] ?: SettingsModel.DEFAULT_URNETWORK_ENABLED,
        urnetworkJwt = this[SettingsKeys.URNETWORK_JWT],
        urnetworkCountryCode = this[SettingsKeys.URNETWORK_COUNTRY_CODE]
            ?: SettingsModel.DEFAULT_URNETWORK_COUNTRY_CODE,
        byedpiWinningArgs = this[SettingsKeys.BYDPI_WINNING_ARGS],
        byedpiDefaultAccepted = this[SettingsKeys.BYDPI_DEFAULT_ACCEPTED]
            ?: SettingsModel.DEFAULT_BYEDPI_DEFAULT_ACCEPTED,
        byedpiUseUiMode = this[SettingsKeys.BYDPI_USE_UI_MODE]
            ?: SettingsModel.DEFAULT_BYEDPI_USE_UI_MODE,
        byedpiUiSettings = ByeDpiUiSettings.fromJson(this[SettingsKeys.BYDPI_UI_SETTINGS_JSON]),
        customDnsServers = readCustomDnsServers(),
        hostsMode = readHostsMode(),
        hosts = readHosts(),
        uiLocaleTag = this[SettingsKeys.UI_LOCALE_TAG],
        appMode = readAppMode(),
        killswitchEnabled = this[SettingsKeys.KILLSWITCH_ENABLED]
            ?: SettingsModel.DEFAULT_KILLSWITCH_ENABLED,
        alwaysOnBannerDismissed = this[SettingsKeys.ALWAYS_ON_BANNER_DISMISSED]
            ?: SettingsModel.DEFAULT_ALWAYS_ON_BANNER_DISMISSED,
    )

    private fun Preferences.readCustomDnsServers(): List<String> {
        val raw = this[SettingsKeys.CUSTOM_DNS_SERVERS] ?: return SettingsModel.DEFAULT_CUSTOM_DNS_SERVERS
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun Preferences.readHostsMode(): HostsMode {
        val raw = this[SettingsKeys.HOSTS_MODE] ?: return SettingsModel.DEFAULT_HOSTS_MODE
        return runCatching { HostsMode.valueOf(raw) }.getOrDefault(SettingsModel.DEFAULT_HOSTS_MODE)
    }

    private fun Preferences.readHosts(): List<String> {
        val raw = this[SettingsKeys.HOSTS_LIST] ?: return SettingsModel.DEFAULT_HOSTS
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun Preferences.readSplitMode(): SplitTunnelMode {
        val raw = this[SettingsKeys.SPLIT_MODE] ?: return SettingsModel.DEFAULT_SPLIT_MODE
        return runCatching { SplitTunnelMode.valueOf(raw) }
            .getOrDefault(SettingsModel.DEFAULT_SPLIT_MODE)
    }

    private fun Preferences.readTrafficMode(): TrafficMode {
        val raw = this[SettingsKeys.TRAFFIC_MODE] ?: return SettingsModel.DEFAULT_TRAFFIC_MODE
        return runCatching { TrafficMode.valueOf(raw) }
            .getOrDefault(SettingsModel.DEFAULT_TRAFFIC_MODE)
    }

    private fun Preferences.readManualEngine(): EngineId? {
        val raw = this[SettingsKeys.MANUAL_ENGINE] ?: return null
        return runCatching { EngineId.valueOf(raw) }.getOrNull()
    }

    private fun Preferences.readEngineAutoPriority(): List<EngineId> {
        val raw = this[SettingsKeys.ENGINE_AUTO_PRIORITY]
            ?: return SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY
        val parsed = raw.split(",")
            .mapNotNull { name -> runCatching { EngineId.valueOf(name.trim()) }.getOrNull() }
            .filter { !it.isStub }
            .distinct()
        if (parsed.isEmpty()) return SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY
        val missing = EngineId.entries.filter { !it.isStub && it !in parsed }
        return parsed + missing
    }

    private fun Preferences.readAppMode(): AppMode {
        val raw = this[SettingsKeys.APP_MODE] ?: return SettingsModel.DEFAULT_APP_MODE
        return runCatching { AppMode.valueOf(raw) }.getOrDefault(SettingsModel.DEFAULT_APP_MODE)
    }

    private companion object {
        private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9.\\-_:]+$")
        private const val IPV4_OCTET = "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
        private val IPV4_REGEX = Regex("^(?:$IPV4_OCTET\\.){3}$IPV4_OCTET$")
        private val IPV6_HEX_REGEX = Regex("^[0-9a-fA-F:.]+$")

        fun isValidDnsAddress(value: String): Boolean {
            if (value.contains(',')) return false
            if (IPV4_REGEX.matches(value)) return true
            return value.contains(':') && IPV6_HEX_REGEX.matches(value)
        }

        fun isValidHostname(value: String): Boolean {
            if (value.length > 253) return false
            return HOSTNAME_REGEX.matches(value)
        }
    }
}
