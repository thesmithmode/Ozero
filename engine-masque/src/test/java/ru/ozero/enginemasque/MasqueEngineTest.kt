package ru.ozero.enginemasque

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel

class MasqueEngineTest {
    private val runtime = mockk<MasqueRuntime>(relaxed = true)
    private val store = mockk<MasqueProfileStore>()
    private val current = MutableStateFlow(MasqueSettings())

    init {
        every { store.settings() } returns current
    }

    @Test
    fun `auto excludes missing disabled corrupt or unavailable profiles but manual selection works`() = runTest {
        var available = true
        val engine = MasqueEngine(runtime, store, backgroundScope) { available }
        assertEquals(EngineId.MASQUE, engine.id)
        assertTrue(engine.capabilities.supportsUdp)
        assertTrue(engine.capabilities.requiresRoutedProbe)
        assertNull(engine.buildManualConfigAwaitingStorage(null))
        current.value = MasqueSettings(profile(), MasqueTransport.H2, false)
        assertNull(engine.buildManualConfigAwaitingStorage(SettingsModel()))
        assertNull(engine.buildManualConfigAwaitingStorage(null))
        val manual = SettingsModel(manualEngine = EngineId.MASQUE, ipv6Enabled = true)
        val expected = EngineConfig.Masque(PROFILE, "H2", true)
        assertEquals(expected, engine.buildManualConfigAwaitingStorage(manual))
        assertEquals(expected, engine.buildProxyConfigAwaitingStorage(manual))
        runCurrent()
        assertEquals(expected, engine.buildManualConfig(manual))
        current.value = current.value.copy(autoEnabled = true)
        assertEquals(EngineConfig.Masque(PROFILE, "H2", false), engine.buildManualConfigAwaitingStorage(null))
        current.value = current.value.copy(corrupt = true)
        assertNull(engine.buildManualConfigAwaitingStorage(manual))
        current.value = current.value.copy(corrupt = false)
        available = false
        assertNull(engine.buildManualConfigAwaitingStorage(manual))
        assertFalse(expected.toString().contains(PROFILE))
    }

    @Test
    fun `successful start exposes actual socks port and routed health and stop clears state`() = runTest {
        val engine = MasqueEngine(runtime, store, backgroundScope) { true }
        coEvery { runtime.start(any(), any(), MasqueTransport.H2, true) } returns MasqueTransport.H2
        coEvery { runtime.isReady() } returns true
        val config = EngineConfig.Masque(PROFILE, "H2", true)
        val result = engine.start(config, Upstream.None) as StartResult.Success
        assertTrue(result.socksPort in 1..65535)
        assertTrue(engine.stats().first().connectedSince > 0)
        assertEquals(
            ExitNodeStrategy.ViaSocks("127.0.0.1", result.socksPort),
            engine.exitNodeStrategy(result.socksPort),
        )
        assertTrue(engine.exitNodeStrategy(0) is ExitNodeStrategy.Unavailable)
        assertTrue(engine.exitNodeStrategy(result.socksPort + 1) is ExitNodeStrategy.Unavailable)
        assertEquals(EnginePlugin.ReadyResult.Ready, engine.awaitReady())
        assertTrue(engine.probe() is ProbeResult.Success)
        coEvery { runtime.isReady() } returns false
        assertTrue(engine.awaitReady() is EnginePlugin.ReadyResult.Timeout)
        assertEquals(
            ProbeResult.Failure.Code.ROUTED_PROBE_FAILED,
            (engine.probe() as ProbeResult.Failure).code,
        )
        engine.stop()
        assertEquals(EngineStats(), engine.stats().first())
        assertTrue(engine.exitNodeStrategy(result.socksPort) is ExitNodeStrategy.Unavailable)
        assertEquals(6_000L, engine.stopTimeoutMs())
        coVerify(exactly = 2) { runtime.stop() }
    }

    @Test
    fun `unsupported configuration and upstream never start a child`() = runTest {
        val engine = MasqueEngine(runtime, store, backgroundScope) { true }
        assertTrue(engine.start(EngineConfig.Warp, Upstream.None) is StartResult.Failure)
        val config = EngineConfig.Masque(PROFILE, "AUTO")
        assertTrue(engine.start(config, Upstream.Socks5("127.0.0.1", 1080)) is StartResult.Failure)
        coVerify(exactly = 0) { runtime.start(any(), any(), any(), any()) }
    }

    @Test
    fun `failed start stays disconnected and cancellation propagates`() = runTest {
        val engine = MasqueEngine(runtime, store, backgroundScope) { true }
        val config = EngineConfig.Masque(PROFILE, "AUTO")
        coEvery { runtime.start(any(), any(), any(), any()) } returns null
        assertTrue(engine.start(config, Upstream.None) is StartResult.Failure)
        coEvery { runtime.start(any(), any(), any(), any()) } throws IllegalStateException("fixture")
        assertTrue(engine.start(config, Upstream.None) is StartResult.Failure)
        coEvery { runtime.start(any(), any(), any(), any()) } throws CancellationException("fixture")
        assertThrows<CancellationException> { engine.start(config, Upstream.None) }
        assertEquals(EngineStats(), engine.stats().first())
    }

    @Test
    fun `storage failures are not interpreted as a registered engine`() = runTest {
        every { store.settings() } returns flow { error("fixture") }
        val engine = MasqueEngine(runtime, store, backgroundScope) { true }
        runCurrent()
        assertNull(engine.buildManualConfig(null))
        assertNull(engine.buildManualConfigAwaitingStorage(null))
        assertNull(engine.buildProxyConfigAwaitingStorage(null))
    }

    @Test
    fun `malformed profile and transport fail without launching a native process`() = runTest {
        val engine = MasqueEngine(runtime, store, backgroundScope) { true }
        assertTrue(engine.start(EngineConfig.Masque("{}", "H2"), Upstream.None) is StartResult.Failure)
        assertTrue(engine.start(EngineConfig.Masque(PROFILE, "invalid"), Upstream.None) is StartResult.Failure)
        coVerify(exactly = 0) { runtime.start(any(), any(), any(), any()) }
        assertEquals(EngineStats(), engine.stats().first())
    }

    private fun profile(): MasqueProfile = MasqueProfile.parse(PROFILE)

    private companion object {
        val PROFILE = """{"private_key":"test","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
    }
}
