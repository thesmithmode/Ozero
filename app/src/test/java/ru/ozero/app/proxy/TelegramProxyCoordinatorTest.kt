package ru.ozero.app.proxy

import android.content.pm.ApplicationInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginetelegram.TelegramConfigStore
import ru.ozero.enginetelegram.TelegramProxyConfig
import ru.ozero.enginetelegram.TelegramProxyService
import ru.ozero.enginetelegram.TelegramProxyState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.Upstream

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramProxyCoordinatorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var tunnelStateFlow: MutableStateFlow<TunnelState>
    private lateinit var configFlow: MutableStateFlow<TelegramProxyConfig>
    private lateinit var mockProxy: TelegramProxyService
    private lateinit var mockTunnelController: TunnelController
    private lateinit var fakeConfigStore: TelegramConfigStore
    private lateinit var coordinator: TelegramProxyCoordinator

    @AfterEach
    fun tearDown() {
        coordinator.stop()
    }

    @BeforeEach
    fun setUp() {
        tunnelStateFlow = MutableStateFlow(TunnelState.Idle)
        configFlow = MutableStateFlow(TelegramProxyConfig())

        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val context = mockk<Context>()
        every { context.applicationInfo } returns appInfo

        mockProxy = mockk(relaxed = true)
        every { mockProxy.state } returns MutableStateFlow(TelegramProxyState.Idle)

        mockTunnelController = mockk()
        every { mockTunnelController.state } returns tunnelStateFlow

        fakeConfigStore = object : TelegramConfigStore {
            override fun config() = configFlow
            override suspend fun setEnabled(value: Boolean) {}
            override suspend fun setPort(value: Int) {}
            override suspend fun setDomain(value: String) {}
            override suspend fun setSecret(value: String) {}
        }

        coordinator = TelegramProxyCoordinator(mockProxy, mockTunnelController, fakeConfigStore, coordinatorScope)
    }

    @Test
    fun `should stop proxy when config disabled`() = testScope.runTest {
        coordinator.start()
        configFlow.value = TelegramProxyConfig(enabled = false, secret = "abc")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        verify(atLeast = 1) { mockProxy.stop() }
    }

    @Test
    fun `should stop proxy when secret blank`() = testScope.runTest {
        coordinator.start()
        configFlow.value = TelegramProxyConfig(enabled = true, secret = "")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        verify(atLeast = 1) { mockProxy.stop() }
    }

    @Test
    fun `should start with Socks5 upstream when socksPort positive`() = testScope.runTest {
        coordinator.start()
        configFlow.value = TelegramProxyConfig(enabled = true, secret = "abc")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        verify { mockProxy.start(any(), Upstream.Socks5("127.0.0.1", 1080)) }
    }

    @Test
    fun `should start with None upstream when WARP socksPort zero`() = testScope.runTest {
        coordinator.start()
        configFlow.value = TelegramProxyConfig(enabled = true, secret = "abc")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)
        verify { mockProxy.start(any(), Upstream.None) }
    }

    @Test
    fun `should stop proxy when tunnel Idle`() = testScope.runTest {
        coordinator.start()
        configFlow.value = TelegramProxyConfig(enabled = true, secret = "abc")
        tunnelStateFlow.value = TunnelState.Idle
        verify(atLeast = 1) { mockProxy.stop() }
    }

    @Test
    fun `should start proxy with correct config object`() = testScope.runTest {
        val config = TelegramProxyConfig(enabled = true, secret = "mysecret", port = 4567)
        coordinator.start()
        configFlow.value = config
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 9090)
        verify { mockProxy.start(config, Upstream.Socks5("127.0.0.1", 9090)) }
    }

    @Test
    fun `coordinator stop() обязан остановить proxyService — иначе зомби mtg-процесс`() =
        testScope.runTest {
            val config = TelegramProxyConfig(enabled = true, secret = "abc")
            coordinator.start()
            configFlow.value = config
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            coordinator.stop()

            verify(atLeast = 1) { mockProxy.stop() }
        }
}
