package ru.ozero.enginewarp

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.VpnSocketProtector
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineWarpLifecycleTest {
    @Test
    fun `successful attach transfers raw fd ownership until stop`() = runTest {
        val closed = mutableListOf<Int>()
        val bridge = FakeWarpSdkBridge()
        val engine = engine(bridge, backgroundScope, closed::add)

        assertIs<StartResult.Success>(engine.start(EngineConfig.Warp, Upstream.None))
        assertIs<TunAttachResult.Success>(engine.attachTun(41))
        engine.stop()

        assertEquals(listOf(41), closed)
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `failed attach leaves raw fd ownership with caller`() = runTest {
        val closed = mutableListOf<Int>()
        val bridge = FakeWarpSdkBridge(
            attachResult = WarpSdkBridge.AttachResult.Failed("rejected"),
        )
        val engine = engine(bridge, backgroundScope, closed::add)

        engine.start(EngineConfig.Warp, Upstream.None)
        assertIs<TunAttachResult.Failure>(engine.attachTun(42))
        engine.stop()

        assertEquals(emptyList(), closed)
    }

    @Test
    fun `successful reattach closes stale owned fd exactly once`() = runTest {
        val closed = mutableListOf<Int>()
        val engine = engine(FakeWarpSdkBridge(), backgroundScope, closed::add)

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(43)
        engine.attachTun(44)
        engine.stop()

        assertEquals(listOf(43, 44), closed)
    }

    @Test
    fun `stop force terminates blocked isolated cleanup`() = runTest {
        val bridge = FakeWarpSdkBridge(blockStopUntilForceTermination = true)
        val engine = engine(
            bridge = bridge,
            scope = backgroundScope,
            stopCleanupTimeoutMs = 20L,
            forceTerminationJoinTimeoutMs = 20L,
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.stop()

        assertEquals(1, bridge.forceTerminateCalls)
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `restart fails closed while previous cleanup remains blocked`() = runTest {
        val bridge = FakeWarpSdkBridge(
            blockStopUntilForceTermination = true,
            releaseCleanupOnForceTermination = false,
        )
        val engine = engine(
            bridge = bridge,
            scope = backgroundScope,
            stopCleanupTimeoutMs = 20L,
            forceTerminationJoinTimeoutMs = 20L,
            startCleanupWaitMs = 20L,
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.stop()
        val restart = engine.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Failure>(restart)
        assertEquals(2, bridge.forceTerminateCalls)
        bridge.releaseCleanup()
        testScheduler.runCurrent()
        assertEquals(1, bridge.detachCalls)
    }

    private fun engine(
        bridge: FakeWarpSdkBridge,
        scope: CoroutineScope,
        rawFdCloser: (Int) -> Unit = {},
        stopCleanupTimeoutMs: Long = 1_250L,
        forceTerminationJoinTimeoutMs: Long = 250L,
        startCleanupWaitMs: Long = 500L,
    ): EngineWarp {
        val store = mockk<WarpConfigSlotStore>()
        every { store.activeSlot() } returns flowOf(
            WarpConfigSlot(
                id = "lifecycle",
                name = "Lifecycle",
                config = SAMPLE_CONFIG,
                isActive = true,
            ),
        )
        return EngineWarp(
            autoConfig = mockk(relaxed = true),
            configStore = store,
            sdkBridge = bridge,
            uapiPathProvider = { "/tmp/uapi" },
            socketProtector = VpnSocketProtector { true },
            handshakeChecker = { _, _ -> true },
            runtimeControl = WarpRuntimeControl(
                pluginScope = scope,
                rawFdCloser = rawFdCloser,
                stopCleanupTimeoutMs = stopCleanupTimeoutMs,
                forceTerminationJoinTimeoutMs = forceTerminationJoinTimeoutMs,
                startCleanupWaitMs = startCleanupWaitMs,
            ),
        )
    }

    private companion object {
        val SAMPLE_CONFIG = WarpConfig(
            privateKey = "private",
            publicKey = "public",
            peerPublicKey = "peer",
            peerEndpoint = "162.159.192.1:2408",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "2606:4700::1/128",
            accountLicense = "license",
        )
    }
}
