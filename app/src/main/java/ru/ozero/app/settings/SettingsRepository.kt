package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<SettingsModel>

    suspend fun setSplitMode(mode: SplitTunnelMode)

    suspend fun setIpv6Enabled(enabled: Boolean)

    suspend fun setAutoStart(enabled: Boolean)

    suspend fun setManualEngine(engine: EngineId?)

    suspend fun setUrnetworkEnabled(enabled: Boolean)

    suspend fun setUrnetworkJwt(jwt: String?)
}

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

    private fun Preferences.toSettingsModel(): SettingsModel = SettingsModel(
        splitMode = readSplitMode(),
        ipv6Enabled = this[SettingsKeys.IPV6_ENABLED] ?: SettingsModel.DEFAULT_IPV6_ENABLED,
        autoStart = this[SettingsKeys.AUTO_START] ?: SettingsModel.DEFAULT_AUTO_START,
        manualEngine = readManualEngine(),
        urnetworkEnabled = this[SettingsKeys.URNETWORK_ENABLED] ?: SettingsModel.DEFAULT_URNETWORK_ENABLED,
        urnetworkJwt = this[SettingsKeys.URNETWORK_JWT],
    )

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
