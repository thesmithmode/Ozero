package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UrnetworkConfigStore {
    fun walletAddress(): Flow<String>
    fun walletOverride(): Flow<String?>
    suspend fun setWalletOverride(value: String?)
    fun byJwt(): Flow<String?>
    suspend fun setByJwt(value: String?)
    fun byClientJwt(): Flow<String?>
    suspend fun setByClientJwt(value: String?)
    fun windowType(): Flow<UrnetworkWindowType> = flowOf(UrnetworkWindowType.AUTO)
    suspend fun setWindowType(value: UrnetworkWindowType) = Unit
    fun fixedIpSize(): Flow<Boolean> = flowOf(false)
    suspend fun setFixedIpSize(value: Boolean) = Unit
    fun provideEnabled(): Flow<Boolean> = flowOf(true)
    suspend fun setProvideEnabled(value: Boolean) = Unit
}
