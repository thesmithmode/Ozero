package ru.ozero.engineurnetwork

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UrnetworkEngineTest {

    private lateinit var delegate: UrnetworkDelegate
    private lateinit var engine: UrnetworkEngine

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
        engine = UrnetworkEngine(delegate)
    }

    @Test
    fun engineIdIsUrnetwork() {
        assertEquals(EngineId.URNETWORK, engine.id)
    }

    @Test
    fun capabilitiesP2P() {
        val caps = engine.capabilities
        assertTrue(caps.supportsTcp)
        assertTrue(caps.supportsUdp)
        assertTrue(!caps.requiresServer, "URnetwork P2P не требует сервера")
    }

    @Test
    fun startRequiresUrnetworkConfig() = runTest {
        val ex = runCatching { engine.start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startFailsOnBlankJwt() = runTest {
        val r = engine.start(EngineConfig.Urnetwork(jwtToken = "  "))
        assertIs<StartResult.Failure>(r)
        assertTrue((r as StartResult.Failure).reason.contains("jwtToken"))
    }

    @Test
    fun startFailsWhenDelegateReturnsFalse() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns false
        val r = engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))
        assertIs<StartResult.Failure>(r)
    }

    @Test
    fun startSuccessWhenDelegateConnectsAndStatusConnected() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.CONNECTED
        every { delegate.sdkVersion() } returns "test-sdk"

        val r = engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt", socksPort = 10810))
        assertIs<StartResult.Success>(r)
        assertEquals(10810, (r as StartResult.Success).socksPort)
    }

    @Test
    fun startFailsWhenStatusNeverBecomesConnected() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.FAILED

        val r = engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))
        assertIs<StartResult.Failure>(r)
    }

    @Test
    fun stopCallsDelegateDisconnect() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.CONNECTED
        engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))

        engine.stop()
        verify { delegate.disconnect() }
    }

    @Test
    fun stopIsIdempotentWithoutStart() = runTest {
        engine.stop()
    }

    @Test
    fun probeFailsWhenNotStarted() = runTest {
        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
        assertTrue((r as ProbeResult.Failure).reason.contains("не запущен"))
    }

    @Test
    fun probeSuccessWhenConnected() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.CONNECTED
        every { delegate.sdkVersion() } returns "test-sdk"
        engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))

        val r = engine.probe()
        assertIs<ProbeResult.Success>(r)
    }

    @Test
    fun probeFailsWhenDisconnected() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returnsMany listOf(
            UrnetworkConnectionStatus.CONNECTED,
            UrnetworkConnectionStatus.DISCONNECTED,
        )
        every { delegate.sdkVersion() } returns "test-sdk"
        engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))

        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
    }

    @Test
    fun startPassesProviderModeToDelegate() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.CONNECTED
        every { delegate.sdkVersion() } returns "test-sdk"

        engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt", mode = "provider"))

        verify { delegate.connect(any(), any(), any(), UrnetworkMode.PROVIDER) }
    }

    @Test
    fun startDefaultsModeToConsumer() = runTest {
        every { delegate.connect(any(), any(), any(), any()) } returns true
        every { delegate.connectionStatus() } returns UrnetworkConnectionStatus.CONNECTED
        every { delegate.sdkVersion() } returns "test-sdk"

        engine.start(EngineConfig.Urnetwork(jwtToken = "test-jwt"))

        verify { delegate.connect(any(), any(), any(), UrnetworkMode.CONSUMER) }
    }

    @Test
    fun statsFlowEmitsInitialValue() = runTest {
        val stats = engine.stats()
        val first = stats.first()
        assertIs<ru.ozero.coreapi.EngineStats>(first)
    }
}
