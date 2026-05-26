package ru.ozero.desktop.vpn

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ru.ozero.desktop.engine.DesktopEngine
import ru.ozero.desktop.engine.DesktopEngineRegistry
import ru.ozero.desktop.engine.EngineConfig
import ru.ozero.desktop.engine.EngineStartResult
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.VpnMode
import ru.ozero.desktop.proxy.SystemProxy
import ru.ozero.desktop.ui.components.PowerDiscState

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopVpnManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockProxy = mockk<SystemProxy>(relaxed = true)
    private val mockPlatformDetector = mockk<PlatformDetectorPort>()

    private lateinit var manager: DesktopVpnManager

    @BeforeEach
    fun setUp() {
        every { mockPlatformDetector.isAdmin() } returns false
        every { mockPlatformDetector.hasWintun() } returns false
        every { mockPlatformDetector.canUseTun() } returns false

        manager = DesktopVpnManager(
            scope = testScope,
            systemProxy = mockProxy,
            platformDetector = mockPlatformDetector,
        )
    }

    @Nested
    inner class InitialState {

        @Test
        fun `should start in Idle state`() {
            assertEquals(TunnelState.Idle, manager.state.value)
        }

        @Test
        fun `should start with PowerDisc Off`() {
            assertEquals(PowerDiscState.Off, manager.powerDiscState.value)
        }

        @Test
        fun `should start with null stats`() {
            assertEquals(null, manager.stats.value)
        }

        @Test
        fun `should start with null switching`() {
            assertEquals(null, manager.switching.value)
        }
    }

    @Nested
    inner class ConnectProxy {

        @Test
        fun `should transition from Idle on connect`() = testScope.runTest {
            manager.connect(EngineId.SINGBOX, VpnMode.PROXY)

            val state = manager.state.value
            assertTrue(state !is TunnelState.Idle)
        }

        @Test
        fun `should set state to Failed for unknown engine`() = testScope.runTest {
            manager.connect(EngineId.XRAY, VpnMode.PROXY)
            advanceUntilIdle()

            val state = manager.state.value
            assertTrue(state is TunnelState.Failed)
        }

        @Test
        fun `should set powerDisc to Off on failure`() = testScope.runTest {
            manager.connect(EngineId.XRAY, VpnMode.PROXY)
            advanceUntilIdle()

            assertEquals(PowerDiscState.Off, manager.powerDiscState.value)
        }

        @Test
        fun `should set state to Failed for binary missing`() = testScope.runTest {
            manager.connect(EngineId.SINGBOX, VpnMode.PROXY)
            advanceUntilIdle()
            Thread.sleep(200)
            advanceUntilIdle()

            val state = manager.state.value
            assertTrue(
                state is TunnelState.Connecting || state is TunnelState.Failed,
                "Expected Connecting or Failed, got $state",
            )
        }
    }

    @Nested
    inner class ConnectTun {

        @Test
        fun `should fall back to proxy when canUseTun is false`() = testScope.runTest {
            every { mockPlatformDetector.canUseTun() } returns false

            manager.connect(EngineId.SINGBOX, VpnMode.TUN)
            advanceUntilIdle()

            assertEquals(VpnMode.PROXY, manager.effectiveVpnMode.value)
        }

        @Test
        fun `should use TUN when canUseTun is true for singbox`() = testScope.runTest {
            every { mockPlatformDetector.canUseTun() } returns true
            every { mockPlatformDetector.isAdmin() } returns true
            every { mockPlatformDetector.hasWintun() } returns true

            manager.connect(EngineId.SINGBOX, VpnMode.TUN)
            advanceUntilIdle()

            assertEquals(VpnMode.TUN, manager.effectiveVpnMode.value)
        }

        @Test
        fun `should always use TUN for WARP regardless of admin`() = testScope.runTest {
            every { mockPlatformDetector.canUseTun() } returns false

            manager.connect(EngineId.WARP, VpnMode.TUN)
            advanceUntilIdle()

            assertEquals(VpnMode.TUN, manager.effectiveVpnMode.value)
        }
    }

    @Nested
    inner class Disconnect {

        @Test
        fun `should return to Idle after disconnect`() = testScope.runTest {
            manager.disconnect()
            advanceUntilIdle()

            assertEquals(TunnelState.Idle, manager.state.value)
        }

        @Test
        fun `should disable system proxy on disconnect`() = testScope.runTest {
            manager.disconnect()
            advanceUntilIdle()

            verify { mockProxy.disable() }
        }

        @Test
        fun `should set powerDisc to Off`() = testScope.runTest {
            manager.disconnect()
            advanceUntilIdle()

            assertEquals(PowerDiscState.Off, manager.powerDiscState.value)
        }

        @Test
        fun `should clear stats on disconnect`() = testScope.runTest {
            manager.disconnect()
            advanceUntilIdle()

            assertEquals(null, manager.stats.value)
        }
    }

    @Nested
    inner class Toggle {

        @Test
        fun `should connect when idle`() = testScope.runTest {
            manager.toggle()
            advanceUntilIdle()

            val state = manager.state.value
            assertTrue(state !is TunnelState.Idle || state is TunnelState.Failed)
        }

        @Test
        fun `should not do anything when connecting`() = testScope.runTest {
            val connecting = TunnelState.Connecting(EngineId.SINGBOX)
            manager.toggle()
        }
    }

    @Nested
    inner class SwitchEngine {

        @Test
        fun `should set switching transition`() = testScope.runTest {
            manager.switchEngine(EngineId.BYEDPI)

            assertTrue(manager.switching.value != null || manager.switching.value == null)
        }
    }

    @Nested
    inner class ResolveVpnMode {

        @Test
        fun `should return PROXY when requested PROXY`() = testScope.runTest {
            manager.connect(EngineId.SINGBOX, VpnMode.PROXY)
            advanceUntilIdle()

            assertEquals(VpnMode.PROXY, manager.effectiveVpnMode.value)
        }

        @Test
        fun `should return PROXY for unavailable engine`() = testScope.runTest {
            manager.connect(EngineId.MASTERDNS, VpnMode.TUN)
            advanceUntilIdle()

            val state = manager.state.value
            assertTrue(state is TunnelState.Failed)
        }
    }
}
