package ru.ozero.enginemasterdns

import kotlinx.coroutines.flow.Flow

data class MasterDnsPersistedConfig(
    val configToml: String = "",
    val resolvers: List<String> = emptyList(),
    val serverIp: String = "",
    val serverPort: Int = 22,
)

interface MasterDnsConfigStore {
    fun config(): Flow<MasterDnsPersistedConfig>
    suspend fun setConfigToml(toml: String)
    suspend fun setResolvers(resolvers: List<String>)
    suspend fun setServerIp(ip: String)
    suspend fun setServerPort(port: Int)
}
