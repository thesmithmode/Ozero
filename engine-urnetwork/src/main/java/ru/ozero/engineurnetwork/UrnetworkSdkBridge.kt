package ru.ozero.engineurnetwork

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val UNKNOWN_CONTRACT_STATUS_FLOW: StateFlow<UrnetworkSdkBridge.ContractStatusSnapshot> =
    MutableStateFlow(UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN).asStateFlow()

@Suppress("TooManyFunctions")
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

    suspend fun initDeviceForLocations(byClientJwt: String, walletAddress: String): Boolean = false
    fun isDeviceAvailable(): Boolean = false

    fun setPreferredLocation(selection: UrnetworkLocationSelection?) {}

    fun setProvidePaused(paused: Boolean)
    fun isProvidePaused(): Boolean

    fun applyPerformanceProfile(
        windowType: UrnetworkWindowType,
        fixedIpSize: Boolean,
        allowDirect: Boolean = true,
    ) {}

    fun setProvideControlMode(mode: UrnetworkProvideControlMode) {}
    fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {}

    fun peerCount(): Int

    fun unpaidByteCount(): Long
    fun fetchTransferStats()

    suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot?

    suspend fun fetchAccountPoints(): AccountPointsSnapshot? = null

    suspend fun fetchNetworkReliability(): Double? = null

    suspend fun fetchReferralCount(): Long? = null

    fun contractStatus(): StateFlow<ContractStatusSnapshot> = UNKNOWN_CONTRACT_STATUS_FLOW

    data class ContractStatusSnapshot(
        val insufficientBalance: Boolean,
        val noPermission: Boolean,
        val premium: Boolean,
    ) {
        companion object {
            val UNKNOWN = ContractStatusSnapshot(
                insufficientBalance = false,
                noPermission = false,
                premium = false,
            )
        }
    }

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

    data class AccountPointsSnapshot(
        val totalPoints: Double,
        val payoutPoints: Double,
        val referralPoints: Double,
        val reliabilityPoints: Double,
        val multiplierPoints: Double,
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
