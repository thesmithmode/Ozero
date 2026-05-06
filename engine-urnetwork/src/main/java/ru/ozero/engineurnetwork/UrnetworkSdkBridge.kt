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

    sealed class StartResult {
        data object Success : StartResult()
        data class Failed(val reason: String) : StartResult()
    }

    sealed class AttachResult {
        data object Success : AttachResult()
        data class Failed(val reason: String) : AttachResult()
    }
}
