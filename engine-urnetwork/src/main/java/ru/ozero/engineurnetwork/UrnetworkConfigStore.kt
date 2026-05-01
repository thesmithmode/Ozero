package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow

interface UrnetworkConfigStore {
    fun walletAddress(): Flow<String>
    fun walletOverride(): Flow<String?>
    suspend fun setWalletOverride(value: String?)
    fun consentGranted(): Flow<Boolean>
    suspend fun markConsentGranted()
    suspend fun revokeConsent()
}
