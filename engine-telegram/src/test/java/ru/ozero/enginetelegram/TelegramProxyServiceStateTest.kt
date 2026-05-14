package ru.ozero.enginetelegram

import android.content.pm.ApplicationInfo
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.enginescore.Upstream
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramProxyServiceStateTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configFlow: MutableStateFlow<TelegramProxyConfig>
    private lateinit var configStore: TelegramConfigStore
    private lateinit var service: TelegramProxyService

    @BeforeEach
    fun setUp() {
        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = tempDir.toFile().absolutePath

        val context = mockk<Context>()
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
}
