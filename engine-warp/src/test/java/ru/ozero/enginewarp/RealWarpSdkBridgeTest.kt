package ru.ozero.enginewarp

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealWarpSdkBridgeTest {

    private lateinit var fakeBackend: FakeAwgBackend
    private lateinit var bridge: RealWarpSdkBridge
    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        fakeBackend = FakeAwgBackend()
        bridge = RealWarpSdkBridge(context = context, backend = fakeBackend)
    }

    @Test
    fun `start возвращает Success при успешном setState UP`() = runTest {
        val result = bridge.start(minimalConfig())
        assertEquals(WarpSdkBridge.StartResult.Success, result)
    }

    @Test
    fun `start вызывает setState с Tunnel_State_UP`() = runTest {
        bridge.start(minimalConfig())
        assertEquals(Tunnel.State.UP, fakeBackend.lastState)
    }

    @Test
    fun `start передаёт ненулевой Config в setState`() = runTest {
        bridge.start(minimalConfig())
        assertTrue(fakeBackend.lastConfig != null)
    }

    @Test
    fun `isRunning true после успешного start`() = runTest {
        bridge.start(minimalConfig())
        assertTrue(bridge.isRunning())
    }

    @Test
    fun `isRunning false до start`() {
        assertFalse(bridge.isRunning())
    }

    @Test
    fun `stop вызывает setState с Tunnel_State_DOWN`() = runTest {
        bridge.start(minimalConfig())
        bridge.stop()
        assertEquals(Tunnel.State.DOWN, fakeBackend.lastState)
    }

    @Test
    fun `isRunning false после stop`() = runTest {
        bridge.start(minimalConfig())
        bridge.stop()
        assertFalse(bridge.isRunning())
    }

    @Test
    fun `start возвращает Failed при исключении из backend`() = runTest {
        fakeBackend.throwOnUp = RuntimeException("backend exploded")
        val result = bridge.start(minimalConfig())
        assertTrue(result is WarpSdkBridge.StartResult.Failed)
        assertTrue((result as WarpSdkBridge.StartResult.Failed).reason.contains("backend exploded"))
    }

    @Test
    fun `isRunning false если start бросил исключение`() = runTest {
        fakeBackend.throwOnUp = RuntimeException("fail")
        bridge.start(minimalConfig())
        assertFalse(bridge.isRunning())
    }

    @Test
    fun `stop не бросает исключение если backend бросает`() = runTest {
        bridge.start(minimalConfig())
        fakeBackend.throwOnDown = RuntimeException("stop fail")
        bridge.stop()
        assertFalse(bridge.isRunning())
    }

    private fun minimalConfig() = WarpConfig(
        privateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        peerPublicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        peerEndpoint = "engage.cloudflareclient.com:4500",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
    )

    private class FakeAwgBackend : AwgBackend {
        var lastState: Tunnel.State? = null
        var lastConfig: Config? = null
        var throwOnUp: Throwable? = null
        var throwOnDown: Throwable? = null

        override fun setState(tunnel: Tunnel, state: Tunnel.State, config: Config?): Tunnel.State {
            lastState = state
            lastConfig = config
            if (state == Tunnel.State.UP) throwOnUp?.let { throw it }
            if (state == Tunnel.State.DOWN) throwOnDown?.let { throw it }
            tunnel.onStateChange(state)
            return state
        }
    }
}
