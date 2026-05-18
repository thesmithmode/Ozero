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
    fun provideControlMode(): Flow<UrnetworkProvideControlMode> = flowOf(UrnetworkProvideControlMode.ALWAYS)
    suspend fun setProvideControlMode(value: UrnetworkProvideControlMode) = Unit
    fun provideNetworkMode(): Flow<UrnetworkProvideNetworkMode> = flowOf(UrnetworkProvideNetworkMode.WIFI)
    suspend fun setProvideNetworkMode(value: UrnetworkProvideNetworkMode) = Unit

    fun selectedLocation(): Flow<UrnetworkLocationSelection> = flowOf(UrnetworkLocationSelection.EMPTY)
    suspend fun setSelectedLocation(value: UrnetworkLocationSelection) = Unit
}

data class UrnetworkLocationSelection(
    val countryCode: String?,
    val region: String?,
    val city: String?,
) {
    fun normalized(): UrnetworkLocationSelection? {
        val cc = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch.isLetter() } }
        val r = region?.trim()?.takeIf { it.isNotEmpty() }
        val c = city?.trim()?.takeIf { it.isNotEmpty() }
        if (cc == null && r == null && c == null) return null
        return UrnetworkLocationSelection(cc, r, c)
    }

    fun summary(): String = buildString {
        append(countryCode ?: "??")
        if (!region.isNullOrBlank()) append('/').append(region)
        if (!city.isNullOrBlank()) append('/').append(city)
    }

    companion object {
        val EMPTY = UrnetworkLocationSelection(null, null, null)
    }
}
