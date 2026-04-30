package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AutoStartGateway
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import javax.inject.Inject

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

    override suspend fun setManualEngine(engine: EngineId?) {
        dataStore.edit { prefs ->
            if (engine == null) {
                prefs.remove(SettingsKeys.MANUAL_ENGINE)
            } else {
                prefs[SettingsKeys.MANUAL_ENGINE] = engine.name
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

    override suspend fun setByedpiWinningArgs(args: String?) {
        dataStore.edit { prefs ->
            if (args.isNullOrBlank()) {
                prefs.remove(SettingsKeys.BYDPI_WINNING_ARGS)
            } else {
                prefs[SettingsKeys.BYDPI_WINNING_ARGS] = args
            }
        }
    }

    override suspend fun setCustomDnsServers(servers: List<String>) {
        dataStore.edit { prefs ->
            val cleaned = servers.map { it.trim() }.filter { it.isNotEmpty() }
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
            val cleaned = hosts.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) {
                prefs.remove(SettingsKeys.HOSTS_LIST)
            } else {
                prefs[SettingsKeys.HOSTS_LIST] = cleaned.joinToString(",")
            }
        }
    }

    private fun Preferences.toSettingsModel(): SettingsModel = SettingsModel(
        splitMode = readSplitMode(),
        ipv6Enabled = this[SettingsKeys.IPV6_ENABLED] ?: SettingsModel.DEFAULT_IPV6_ENABLED,
        autoStart = this[SettingsKeys.AUTO_START] ?: SettingsModel.DEFAULT_AUTO_START,
        manualEngine = readManualEngine(),
        urnetworkEnabled = this[SettingsKeys.URNETWORK_ENABLED] ?: SettingsModel.DEFAULT_URNETWORK_ENABLED,
        urnetworkJwt = this[SettingsKeys.URNETWORK_JWT],
        byedpiWinningArgs = this[SettingsKeys.BYDPI_WINNING_ARGS],
        customDnsServers = readCustomDnsServers(),
        hostsMode = readHostsMode(),
        hosts = readHosts(),
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

    private fun Preferences.readManualEngine(): EngineId? {
        val raw = this[SettingsKeys.MANUAL_ENGINE] ?: return null
        return runCatching { EngineId.valueOf(raw) }.getOrNull()
    }
}
