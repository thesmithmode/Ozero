package ru.ozero.engineurnetwork

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UrnetworkSdkBridgeDefaultsTest {

    @Test
    fun `default methods expose conservative fallbacks`() = runTest {
        val bridge = FakeBridge()

        assertNull(bridge.selectedLocationInfo())
        assertFalse(bridge.initDeviceForLocations("client", "wallet"))
        assertFalse(bridge.isDeviceAvailable())
        bridge.setPreferredLocation(UrnetworkLocationSelection("DE", null, null))
        bridge.connectPreferredLocation()
        bridge.applyPerformanceProfile(UrnetworkWindowType.SPEED, fixedIpSize = true)
        bridge.setProvideControlMode(UrnetworkProvideControlMode.AUTO)
        bridge.setProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        assertEquals("unavailable", bridge.relayDiagnostics())
        assertNull(bridge.connectionStatus())
        assertNull(bridge.fetchAccountPoints())
        assertNull(bridge.fetchNetworkReliability())
        assertNull(bridge.fetchReferralCount())
        assertEquals(UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN, bridge.contractStatus().value)
    }

    @Test
    fun `runtimeSnapshot uses status and peer count when available`() {
        val bridge = FakeBridge(status = "connected", peers = 3)

        val snapshot = bridge.runtimeSnapshot()

        assertEquals("connected", snapshot.connectionStatus)
        assertEquals(3, snapshot.peers)
        assertFalse(snapshot.tunnelStarted)
        assertFalse(snapshot.connectIssued)
    }

    @Test
    fun `runtimeSnapshot falls back when status or peers throw`() {
        val snapshot = FakeBridge(throwStatus = true, throwPeers = true).runtimeSnapshot()

        assertNull(snapshot.connectionStatus)
        assertEquals(0, snapshot.peers)
    }

    @Test
    fun `location token defaults are nullable and not best available`() {
        val token = object : UrnetworkSdkBridge.LocationToken {
            override val countryCode: String? = "DE"
        }

        assertEquals("DE", token.countryCode)
        assertNull(token.region)
        assertNull(token.city)
        assertFalse(token.bestAvailable)
    }

    private class FakeBridge(
        private val status: String? = null,
        private val peers: Int = 0,
        private val throwStatus: Boolean = false,
        private val throwPeers: Boolean = false,
    ) : UrnetworkSdkBridge {
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success

        override suspend fun stop() = Unit

        override fun isRunning(): Boolean = false

        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Failed("not running")

        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit

        override fun connectBestAvailable() = Unit

        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null

        override fun openLocationsViewController(): LocationsViewController? = null

        override fun setProvidePaused(paused: Boolean) = Unit

        override fun isProvidePaused(): Boolean = false

        override fun connectionStatus(): String? {
            if (throwStatus) error("status failed")
            return status
        }

        override fun peerCount(): Int {
            if (throwPeers) error("peers failed")
            return peers
        }

        override fun unpaidByteCount(): Long = 0L

        override fun fetchTransferStats() = Unit

        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }
}
