package ru.ozero.enginescore.settings

import kotlinx.coroutines.flow.Flow
import ru.ozero.enginescore.EngineId

@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<SettingsModel>

    suspend fun setSplitMode(mode: SplitTunnelMode)

    suspend fun setIpv6Enabled(enabled: Boolean)

    suspend fun setAutoStart(enabled: Boolean)

    suspend fun setManualEngine(engine: EngineId?)

    suspend fun setEngineAutoPriority(priority: List<EngineId>) = Unit

    suspend fun setUrnetworkEnabled(enabled: Boolean)

    suspend fun setUrnetworkJwt(jwt: String?)

    suspend fun setByedpiWinningArgs(args: String?)

    suspend fun setCustomDnsServers(servers: List<String>)

    suspend fun setHostsMode(mode: HostsMode)

    suspend fun setHosts(hosts: List<String>)

    suspend fun setUiLocaleTag(tag: String?)

    suspend fun setAppMode(mode: AppMode)
}
