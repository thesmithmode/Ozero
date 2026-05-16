package ru.ozero.enginetelegram

import android.content.pm.ApplicationInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.enginescore.Upstream
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramProxyServiceStateTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configFlow: MutableStateFlow<TelegramProxyConfig>
    private lateinit var configStore: TelegramConfigStore
    private lateinit var context: Context
    private lateinit var service: TelegramProxyService

    @BeforeEach
    fun setUp() {
        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = tempDir.toFile().absolutePath

        context = mockk<Context>()
        every { context.applicationInfo } returns appInfo

        configFlow = MutableStateFlow(TelegramProxyConfig())
        configStore = object : TelegramConfigStore {
            override fun config() = configFlow
            override suspend fun setEnabled(v: Boolean) {
                configFlow.value = configFlow.value.copy(enabled = v)
            }
            override suspend fun setPort(v: Int) {
                configFlow.value = configFlow.value.copy(port = v)
            }
            override suspend fun setDomain(v: String) {
                configFlow.value = configFlow.value.copy(domain = v)
            }
            override suspend fun setSecret(v: String) {
                configFlow.value = configFlow.value.copy(secret = v)
            }
        }

        service = TelegramProxyService(context, configStore)
    }

    @AfterEach
    fun tearDown() {
        service.stop()
    }

    private fun makeService(mockWrapper: MtgWrapper, startupCheckMs: Long = 50L) =
        TelegramProxyService(
            context = context,
            configStore = configStore,
            wrapperFactory = { _ -> mockWrapper },
            startupCheckMs = startupCheckMs,
        )

    private fun awaitState(
        svc: TelegramProxyService,
        condition: (TelegramProxyState) -> Boolean,
        timeoutMs: Long = 3_000,
    ): TelegramProxyState {
        val latch = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            svc.state.collect { if (condition(it)) latch.countDown() }
        }
        assertTrue(latch.await(timeoutMs, TimeUnit.MILLISECONDS), "Timeout waiting for expected state")
        scope.cancel()
        return svc.state.value
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(TelegramProxyState.Idle, service.state.first())
    }

    @Test
    fun `should stay Idle when config disabled`() = runTest {
        service.start(TelegramProxyConfig(enabled = false, secret = "abc"), Upstream.None)
        assertEquals(TelegramProxyState.Idle, service.state.first())
    }

    @Test
    fun `should stay Idle when secret blank`() = runTest {
        service.start(TelegramProxyConfig(enabled = true, secret = ""), Upstream.None)
        assertEquals(TelegramProxyState.Idle, service.state.first())
    }

    @Test
    fun `should stay Idle when both disabled and secret blank`() = runTest {
        service.start(TelegramProxyConfig(enabled = false, secret = ""), Upstream.None)
        assertEquals(TelegramProxyState.Idle, service.state.first())
    }

    @Test
    fun `stop should set state to Idle`() = runTest {
        service.stop()
        assertEquals(TelegramProxyState.Idle, service.state.first())
    }

    @Test
    fun `should reach Starting state before startProxy returns`() {
        val mockWrapper = mockk<MtgWrapper>()
        val startingSignal = CountDownLatch(1)
        val exitLatch = CountDownLatch(1)
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor() } answers { exitLatch.await(); 0 }
        every { mockWrapper.startProxy(any(), any(), any(), any()) } answers {
            startingSignal.countDown()
            exitLatch.await()
            mockProcess
        }

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        assertTrue(startingSignal.await(3, TimeUnit.SECONDS), "startProxy never called")
        awaitState(svc) { it is TelegramProxyState.Starting }
        assertIs<TelegramProxyState.Starting>(svc.state.value)
        exitLatch.countDown()
        svc.stop()
    }

    @Test
    fun `should transition to Running when process alive after startup delay`() {
        val mockWrapper = mockk<MtgWrapper>()
        val exitLatch = CountDownLatch(1)
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor() } answers { exitLatch.await(); 0 }
        every { mockWrapper.startProxy(any(), any(), any(), any()) } returns mockProcess

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        val state = awaitState(svc) { it is TelegramProxyState.Running }
        assertIs<TelegramProxyState.Running>(state)
        assertEquals(8080, state.port)
        assertEquals("abc", state.secret)
        exitLatch.countDown()
        svc.stop()
    }

    @Test
    fun `should set Error when process exits before startup delay completes`() {
        val mockWrapper = mockk<MtgWrapper>()
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns false
        every { mockProcess.inputStream } returns "startup error output".byteInputStream()
        every { mockWrapper.startProxy(any(), any(), any(), any()) } returns mockProcess

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        val state = awaitState(svc) { it is TelegramProxyState.Error }
        assertIs<TelegramProxyState.Error>(state)
        assertTrue(state.message.contains("startup error output"), "message='${state.message}'")
        svc.stop()
    }

    @Test
    fun `should set Error when process exits unexpectedly after Running`() {
        val mockWrapper = mockk<MtgWrapper>()
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor() } returns 0
        every { mockWrapper.startProxy(any(), any(), any(), any()) } returns mockProcess

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        awaitState(svc) { it is TelegramProxyState.Running }
        val state = awaitState(svc) { it is TelegramProxyState.Error }
        assertIs<TelegramProxyState.Error>(state)
        assertTrue(state.message.contains("unexpectedly"), "message='${state.message}'")
        svc.stop()
    }

    @Test
    fun `should set Error when startProxy throws exception`() {
        val mockWrapper = mockk<MtgWrapper>()
        every { mockWrapper.startProxy(any(), any(), any(), any()) } throws RuntimeException("binary not found")

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        val state = awaitState(svc) { it is TelegramProxyState.Error }
        assertIs<TelegramProxyState.Error>(state)
        assertTrue(state.message.contains("binary not found"), "message='${state.message}'")
        svc.stop()
    }

    @Test
    fun `stop should transition to Idle from Running state`() {
        val mockWrapper = mockk<MtgWrapper>()
        val exitLatch = CountDownLatch(1)
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor() } answers { exitLatch.await(); 0 }
        every { mockWrapper.startProxy(any(), any(), any(), any()) } returns mockProcess

        val svc = makeService(mockWrapper)
        svc.start(TelegramProxyConfig(enabled = true, secret = "abc", port = 8080), Upstream.None)
        awaitState(svc) { it is TelegramProxyState.Running }
        svc.stop()
        assertIs<TelegramProxyState.Idle>(svc.state.value)
        exitLatch.countDown()
    }

    @Test
    fun `Running toString should not expose secret`() {
        val state = TelegramProxyState.Running(port = 8080, secret = "supersecret123")
        val str = state.toString()
        assertTrue(!str.contains("supersecret123"), "toString must not expose secret: $str")
        assertTrue(str.contains("REDACTED"), "toString should indicate redaction: $str")
    }

    @Test
    fun `should pass socks5 upstream url to startProxy`() {
        val mockWrapper = mockk<MtgWrapper>()
        var capturedUpstream: String? = "NOT_CALLED"
        val exitLatch = CountDownLatch(1)
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor() } answers { exitLatch.await(); 0 }
        every { mockWrapper.startProxy(any(), any(), any(), any()) } answers {
            capturedUpstream = arg<String?>(3)
            mockProcess
        }

        val svc = makeService(mockWrapper)
        svc.start(
            TelegramProxyConfig(enabled = true, secret = "abc", port = 8080),
            Upstream.Socks5("127.0.0.1", 9050),
        )
        awaitState(svc) { it is TelegramProxyState.Running }
        assertEquals("socks5://127.0.0.1:9050", capturedUpstream)
        exitLatch.countDown()
        svc.stop()
    }
}
