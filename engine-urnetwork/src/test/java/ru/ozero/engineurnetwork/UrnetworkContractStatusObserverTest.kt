package ru.ozero.engineurnetwork

import com.bringyour.sdk.LocationsViewController
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkContractStatusObserverTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var contractFlow: MutableStateFlow<UrnetworkSdkBridge.ContractStatusSnapshot>
    private lateinit var tunnelFlow: MutableStateFlow<TunnelState>
    private lateinit var bridge: FakeBridge
    private lateinit var failureReasons: MutableList<String>
    private lateinit var observer: UrnetworkContractStatusObserver

    @BeforeEach
    fun setUp() {
        contractFlow = MutableStateFlow(UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN)
        tunnelFlow = MutableStateFlow<TunnelState>(TunnelState.Idle)
        bridge = FakeBridge(contractFlow)
        val tunnelController = mockk<TunnelController>()
        every { tunnelController.state } returns tunnelFlow
        failureReasons = mutableListOf()
        observer = UrnetworkContractStatusObserver(
            bridge = bridge,
            tunnelController = tunnelController,
            reportEngineFailure = { reason -> failureReasons += reason },
            scope = scope,
        )
        observer.start()
    }

    @Test
    fun `insufficient balance при URnetwork Connected → reportEngineFailure вызывается`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertEquals(listOf("urnetwork-insufficient-balance"), failureReasons)
    }

    @Test
    fun `insufficient balance при URnetwork Connecting → reportEngineFailure вызывается`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connecting(EngineId.URNETWORK)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertEquals(1, failureReasons.size)
    }

    @Test
    fun `premium=true при insufficientBalance — НЕ вызывается failure`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = true,
        )

        assertTrue(failureReasons.isEmpty(), "premium-юзеры не должны автоотключаться")
    }

    @Test
    fun `Idle tunnel state при insufficientBalance — НЕ вызывается failure`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Idle
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertTrue(failureReasons.isEmpty())
    }

    @Test
    fun `другой engine активен — insufficient balance URnetwork не стопит`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertTrue(failureReasons.isEmpty(), "другой engine — URnetwork balance не учитывается")
    }

    @Test
    fun `повторный insufficient balance при том же активном URnetwork — без двойного failure`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        val snapshot = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )
        contractFlow.value = snapshot
        contractFlow.value = snapshot.copy(noPermission = true)
        contractFlow.value = snapshot

        assertEquals(1, failureReasons.size, "disconnectInFlight guard защищает от двойного failure")
    }

    @Test
    fun `disconnect → reconnect → новый insufficient снова reports failure`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )
        assertEquals(1, failureReasons.size)

        tunnelFlow.value = TunnelState.Idle
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot.UNKNOWN

        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertEquals(2, failureReasons.size, "после Idle disconnectInFlight=false → новое срабатывание разрешено")
    }

    @Test
    fun `warnings flow emits InsufficientBalance event на срабатывании`() = runTest(dispatcher) {
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )
        val warning = observer.warnings.first()
        assertNotNull(warning)
        assertEquals(UrnetworkContractStatusObserver.Warning.InsufficientBalance, warning)
    }

    @Test
    fun `stop() отменяет подписку — последующие изменения игнорируются`() = runTest(dispatcher) {
        observer.stop()
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertTrue(failureReasons.isEmpty(), "после stop() observer не реагирует")
    }

    @Test
    fun `start() второй раз заменяет job — без удвоения failure`() = runTest(dispatcher) {
        observer.start()
        observer.start()
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )

        assertEquals(1, failureReasons.size, "несколько start() не должны умножать failure")
    }

    @Test
    fun `reportEngineFailure бросает исключение — observer не падает`() = runTest(dispatcher) {
        val crashingObserver = UrnetworkContractStatusObserver(
            bridge = bridge,
            tunnelController = mockk<TunnelController>().also {
                every { it.state } returns tunnelFlow
            },
            reportEngineFailure = { error("failure hook crash") },
            scope = scope,
        )
        crashingObserver.start()
        tunnelFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        contractFlow.value = UrnetworkSdkBridge.ContractStatusSnapshot(
            insufficientBalance = true,
            noPermission = false,
            premium = false,
        )
    }

    private class FakeBridge(
        private val contractFlow: StateFlow<UrnetworkSdkBridge.ContractStatusSnapshot>,
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
            UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = false
        override fun peerCount(): Int = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
        override fun contractStatus(): StateFlow<UrnetworkSdkBridge.ContractStatusSnapshot> = contractFlow
    }
}
