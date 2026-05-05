package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow

interface UrnetworkConfigStore {
    fun walletAddress(): Flow<String>
    fun walletOverride(): Flow<String?>
    suspend fun setWalletOverride(value: String?)
    fun byJwt(): Flow<String?>
    suspend fun setByJwt(value: String?)
    fun byClientJwt(): Flow<String?>
    suspend fun setByClientJwt(value: String?)
}
