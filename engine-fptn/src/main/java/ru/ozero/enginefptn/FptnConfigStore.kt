package ru.ozero.enginefptn

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface FptnConfigStore {
    fun config(): Flow<FptnConfig>
    fun currentConfig(): FptnConfig
    suspend fun update(transform: (FptnConfig) -> FptnConfig)
}

data class FptnConfig(
    val token: String = "",
    val selectedServerName: String? = null,
    val bypassMethod: String = FptnBypassMethod.DEFAULT.strategyName,
    val sniDomain: String = DEFAULT_SNI_DOMAIN,
    val autoSelect: Boolean = true,
    val reconnectOnNetworkChange: Boolean = true,
    val reconnectOnIpChange: Boolean = false,
    val maxReconnectAttempts: Int = 5,
    val reconnectPauseSeconds: Int = 2,
    val resetServerOnDisconnect: Boolean = true,
) {
    companion object {
        const val DEFAULT_SNI_DOMAIN = "ads.x5.ru"
    }
}

class InMemoryFptnConfigStore(initial: FptnConfig = FptnConfig()) : FptnConfigStore {
    private val state = MutableStateFlow(initial)
    val snapshot: FptnConfig get() = state.value
    fun inject(transform: (FptnConfig) -> FptnConfig) {
        state.value = transform(state.value)
    }
    override fun config(): Flow<FptnConfig> = state
    override fun currentConfig(): FptnConfig = state.value
    override suspend fun update(transform: (FptnConfig) -> FptnConfig) {
        state.value = transform(state.value)
    }
}
