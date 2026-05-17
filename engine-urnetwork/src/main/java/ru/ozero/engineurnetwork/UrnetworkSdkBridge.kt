package ru.ozero.engineurnetwork

import com.bringyour.sdk.LocationsViewController

interface UrnetworkSdkBridge {
    suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): StartResult
    suspend fun stop()
    fun isRunning(): Boolean
    suspend fun attachTun(tunFd: Int): AttachResult

    fun connectTo(location: LocationToken)
    fun connectBestAvailable()
    fun selectedLocation(): LocationToken?
    fun selectedLocationInfo(): LocationInfo? = null
    fun openLocationsViewController(): LocationsViewController?

    fun setPreferredCountry(code: String?) {}

    fun setProvidePaused(paused: Boolean)
    fun isProvidePaused(): Boolean

    fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {}

    fun setProvideControlMode(mode: UrnetworkProvideControlMode) {}
    fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {}

    fun peerCount(): Int

    fun unpaidByteCount(): Long
    fun fetchTransferStats()

    suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot?

    interface LocationToken {
        val countryCode: String?
        val region: String?
            get() = null
        val city: String?
            get() = null
    }

    data class LocationInfo(
        val country: String?,
        val countryCode: String?,
        val name: String?,
    )

    data class SubscriptionBalanceSnapshot(
        val balanceBytes: Long,
        val pendingBytes: Long,
        val startBalanceBytes: Long,
        val usedBytes: Long,
        val plan: String?,
        val store: String?,
    )

    sealed class StartResult {
        data object Success : StartResult()
        data class Failed(val reason: String) : StartResult()
    }

    sealed class AttachResult {
        data object Success : AttachResult()
        data class Failed(val reason: String) : AttachResult()
    }
}
