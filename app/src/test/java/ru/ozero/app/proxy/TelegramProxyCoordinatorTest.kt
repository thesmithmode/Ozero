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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramProxyCoordinatorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var tunnelStateFlow: MutableStateFlow<TunnelState>
    private lateinit var configFlow: MutableStateFlow<TelegramProxyConfig>
    private lateinit var proxyStateFlow: MutableStateFlow<TelegramProxyState>
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
        proxyStateFlow = MutableStateFlow<TelegramProxyState>(TelegramProxyState.Idle)

        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val context = mockk<Context>()
        every { context.applicationInfo } returns appInfo

        mockProxy = mockk(relaxed = true)
        every { mockProxy.state } returns proxyStateFlow

        mockTunnelController = mockk()
        every { mockTunnelController.state } returns tunnelStateFlow

        fakeConfigStore = object : TelegramConfigStore {
            override fun config() = configFlow
            override suspend fun setEnabled(value: Boolean) {}
            override suspend fun setPort(value: Int) {}
            override suspend fun setDomain(value: String) {}
            override suspend fun setSecret(value: String) {}
        }

        coordinator = TelegramProxyCoordinator(
            mockProxy,
            mockTunnelController,
            fakeConfigStore,
            coordinatorScope,
        )
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

    @Test
    fun `Error state при tunnel Connected → restart proxy с тем же config`() = testScope.runTest {
        val config = TelegramProxyConfig(enabled = true, secret = "abc")
        coordinator.start()
        configFlow.value = config
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        proxyStateFlow.value = TelegramProxyState.Error("mtg exited unexpectedly")

        verify(atLeast = 2) {
            mockProxy.start(config, Upstream.Socks5("127.0.0.1", 1080))
        }
    }

    @Test
    fun `restart attempts ограничены maxRestarts — после лимита start не вызывается`() =
        testScope.runTest {
            val coord = TelegramProxyCoordinator(
                proxyService = mockProxy,
                tunnelController = mockTunnelController,
                configStore = fakeConfigStore,
                scope = coordinatorScope,
                maxRestarts = 2,
            )
            val config = TelegramProxyConfig(enabled = true, secret = "abc")
            coord.start()
            configFlow.value = config
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            proxyStateFlow.value = TelegramProxyState.Error("mtg crash 1")
            proxyStateFlow.value = TelegramProxyState.Error("mtg crash 2")
            proxyStateFlow.value = TelegramProxyState.Error("mtg crash 3")
            proxyStateFlow.value = TelegramProxyState.Error("mtg crash 4")

            verify(atLeast = 1, atMost = 3) {
                mockProxy.start(config, Upstream.Socks5("127.0.0.1", 1080))
            }

            coord.stop()
        }

    @Test
    fun `Running state сбрасывает restart counter — последующий crash снова получает retry`() =
        testScope.runTest {
            val coord = TelegramProxyCoordinator(
                proxyService = mockProxy,
                tunnelController = mockTunnelController,
                configStore = fakeConfigStore,
                scope = coordinatorScope,
                maxRestarts = 1,
            )
            val config = TelegramProxyConfig(enabled = true, secret = "abc")
            coord.start()
            configFlow.value = config
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            proxyStateFlow.value = TelegramProxyState.Error("crash1")
            proxyStateFlow.value = TelegramProxyState.Running(443, "secret")
            proxyStateFlow.value = TelegramProxyState.Error("crash2")

            verify(atLeast = 3) {
                mockProxy.start(config, Upstream.Socks5("127.0.0.1", 1080))
            }

            coord.stop()
        }

    @Test
    fun `Error при tunnel Idle не вызывает restart — killswitch domain а не Telegram`() =
        testScope.runTest {
            val config = TelegramProxyConfig(enabled = true, secret = "abc")
            coordinator.start()
            configFlow.value = config
            tunnelStateFlow.value = TunnelState.Idle

            proxyStateFlow.value = TelegramProxyState.Error("mtg crash while tunnel down")

            verify(atLeast = 1) { mockProxy.stop() }
        }

    @Test
    fun `job hold через AtomicReference — concurrency race fix`() {
        val src = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/proxy/TelegramProxyCoordinator.kt",
        ).readText()
        assertFalse(
            src.contains("private var job:") || src.contains("var job: Job?"),
            "job не должен быть plain var Job? — race между start/stop. Использовать AtomicReference.",
        )
        assertTrue(
            src.contains("AtomicReference<Job?>"),
            "jobRef обязан быть AtomicReference<Job?>",
        )
        assertTrue(
            src.contains("jobRef.getAndSet("),
            "start/stop обязан использовать getAndSet для атомарной замены и cancel предыдущего.",
        )
    }

    @Test
    fun `coordinator подписан на proxyService state — обязательно для crash detection`() {
        val src = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/proxy/TelegramProxyCoordinator.kt",
        ).readText()
        assertTrue(
            src.contains("proxyService.state"),
            "Coordinator обязан подписаться на proxyService.state — иначе mtg crash остаётся " +
                "незамеченным до следующего изменения tunnel/config (subprocess не перезапускается).",
        )
        assertTrue(
            src.contains("TelegramProxyState.Error"),
            "Coordinator обязан реагировать на Error для auto-restart subprocess.",
        )
    }

    @Test
    fun `restart counter ограничен maxRestarts — иначе бесконечный crash loop`() {
        val src = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/proxy/TelegramProxyCoordinator.kt",
        ).readText()
        assertTrue(
            src.contains("AtomicInteger") || src.contains("restartCount"),
            "Coordinator обязан хранить счётчик попыток restart — без лимита crash loop " +
                "буде дёргать proxyService.start бесконечно при persistent mtg crash.",
        )
        assertTrue(
            src.contains("maxRestarts"),
            "Coordinator обязан принимать maxRestarts параметром для тестируемости.",
        )
    }
}
