package ru.ozero.engineurnetwork

import com.bringyour.sdk.ConnectLocation
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

    fun connectTo(location: ConnectLocation)
    fun connectBestAvailable()
    fun selectedLocation(): ConnectLocation?
    fun openLocationsViewController(): LocationsViewController?

    fun setPreferredCountry(code: String?) {}

    fun setProvidePaused(paused: Boolean)
    fun isProvidePaused(): Boolean

    fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {}

    fun peerCount(): Int

    fun unpaidByteCount(): Long
    fun fetchTransferStats()

    suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot?

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
