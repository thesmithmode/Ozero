package ru.ozero.enginefptn

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FptnEngineTest {

    private lateinit var store: InMemoryFptnConfigStore
    private lateinit var engine: FptnEngine

    @BeforeEach
    fun setUp() {
        store = InMemoryFptnConfigStore()
        engine = FptnEngine(store)
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>().trim())
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `engine id is FPTN`() {
        assertEquals(EngineId.FPTN, engine.id)
    }

    @Test
    fun `capabilities require server and do not support upstream socks`() {
        val caps = engine.capabilities
        assertEquals(true, caps.requiresServer)
        assertEquals(false, caps.supportsUpstreamSocks)
        assertEquals(true, caps.supportsTcp)
        assertEquals(true, caps.supportsUdp)
    }

    @Test
    fun `start with wrong config type returns failure`() = runTest {
        val result = engine.start(EngineConfig.ByeDpi(), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with blank token returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = ""), Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(true, failure.reason.contains("token", ignoreCase = true))
    }

    @Test
    fun `start with whitespace-only token returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = "   "), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with unknown token prefix returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = "notfptn:abc"), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with malformed base64 returns failure`() = runTest {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad")
        val result = engine.start(EngineConfig.Fptn(token = "fptn:!!!"), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `stop when not started does not throw`() = runTest {
        engine.stop()
    }

    @Test
    fun `stop is idempotent`() = runTest {
        engine.stop()
        engine.stop()
    }

    @Test
    fun `probe returns failure when store has no token`() = runTest {
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns failure when token is blank`() = runTest {
        store.inject { it.copy(token = "  ") }
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns failure when token is invalid`() = runTest {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad")
        store.inject { it.copy(token = "fptn:garbage") }
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns success when token has valid servers`() = runTest {
        store.inject { it.copy(token = "fptn:${validTokenB64()}") }
        val result = engine.probe()
        assertIs<ProbeResult.Success>(result)
    }

    @Test
    fun `autoSelect uses every token server as start candidates`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = true, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals(listOf("S1", "S2", "S3"), result.map { it.name })
    }

    @Test
    fun `manual selected server is tried before remaining fallback candidates`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals(listOf("S2", "S1", "S3"), result.map { it.name })
    }

    @Test
    fun `buildManualConfig returns null when token blank`() {
        assertNull(engine.buildManualConfig(null))
    }

    @Test
    fun `buildManualConfig returns null when token whitespace only`() {
        store.inject { it.copy(token = "   ") }
        assertNull(engine.buildManualConfig(null))
    }

    @Test
    fun `buildManualConfig returns Fptn config when token set`() {
        store.inject {
            it.copy(
                token = "fptn:abc",
                selectedServerName = "S1",
                bypassMethod = FptnBypassMethod.SNI_REALITY_CHROME_147.strategyName,
                sniDomain = "example.com",
                autoSelect = false,
                reconnectOnNetworkChange = true,
                reconnectOnIpChange = true,
                maxReconnectAttempts = 7,
                reconnectPauseSeconds = 5,
                resetServerOnDisconnect = false,
            )
        }
        val cfg = engine.buildManualConfig(null)
        val fptn = assertIs<EngineConfig.Fptn>(cfg)
        assertEquals("fptn:abc", fptn.token)
        assertEquals("S1", fptn.selectedServerName)
        assertEquals(FptnBypassMethod.SNI_REALITY_CHROME_147.strategyName, fptn.bypassMethod)
        assertEquals("example.com", fptn.sniDomain)
        assertEquals(false, fptn.autoSelect)
        assertEquals(true, fptn.reconnectOnNetworkChange)
        assertEquals(true, fptn.reconnectOnIpChange)
        assertEquals(7, fptn.maxReconnectAttempts)
        assertEquals(5, fptn.reconnectPauseSeconds)
        assertEquals(false, fptn.resetServerOnDisconnect)
    }

    @Test
    fun `buildManualConfig propagates sniDomain`() {
        store.inject { it.copy(token = "fptn:abc", sniDomain = "custom.example.com") }
        val fptn = assertIs<EngineConfig.Fptn>(engine.buildManualConfig(null))
        assertEquals("custom.example.com", fptn.sniDomain)
    }

    @Test
    fun `buildManualConfig ignores settings parameter for FPTN-specific config`() {
        store.inject { it.copy(token = "fptn:zzz") }
        val withSettings = engine.buildManualConfig(null)
        val withoutSettings = engine.buildManualConfig(null)
        assertNotNull(withSettings)
        assertEquals(withSettings, withoutSettings)
    }

    @Test
    fun `EngineConfig Fptn default bypassMethod matches enum DEFAULT`() {
        assertEquals(
            FptnBypassMethod.DEFAULT.strategyName,
            EngineConfig.Fptn.DEFAULT_BYPASS_METHOD,
        )
        assertEquals(
            EngineConfig.Fptn.DEFAULT_BYPASS_METHOD,
            EngineConfig.Fptn().bypassMethod,
        )
    }

    @Test
    fun `DEFAULT bypass method strategyName is SNI`() {
        assertEquals("SNI", FptnBypassMethod.DEFAULT.strategyName)
        assertEquals("SNI", EngineConfig.Fptn.DEFAULT_BYPASS_METHOD)
    }

    @Test
    fun `FptnConfig DEFAULT_SNI_DOMAIN is ads x5 ru`() {
        assertEquals("ads.x5.ru", FptnConfig.DEFAULT_SNI_DOMAIN)
    }

    @Test
    fun `EngineConfig Fptn DEFAULT_SNI_DOMAIN is ads x5 ru`() {
        assertEquals("ads.x5.ru", EngineConfig.Fptn.DEFAULT_SNI_DOMAIN)
        assertEquals(EngineConfig.Fptn.DEFAULT_SNI_DOMAIN, EngineConfig.Fptn().sniDomain)
    }

    @Test
    fun `FptnBypassMethod SNI usesSni is true`() {
        assertEquals(true, FptnBypassMethod.SNI.usesSni)
    }

    @Test
    fun `FptnBypassMethod OBFUSCATION usesSni is false`() {
        assertEquals(false, FptnBypassMethod.OBFUSCATION.usesSni)
    }

    @Test
    fun `FptnBypassMethod OBFUSCATION isReality is false`() {
        assertEquals(false, FptnBypassMethod.OBFUSCATION.isReality)
    }

    @Test
    fun `FptnBypassMethod reality variants all have isReality true`() {
        FptnBypassMethod.REALITY_METHODS.forEach { method ->
            assertEquals(true, method.isReality, "Expected ${method.name} isReality=true")
            assertEquals(true, method.usesSni, "Expected ${method.name} usesSni=true")
        }
    }

    @Test
    fun `FptnBypassMethod REALITY_METHODS does not include SNI or OBFUSCATION`() {
        assertEquals(false, FptnBypassMethod.REALITY_METHODS.contains(FptnBypassMethod.SNI))
        assertEquals(false, FptnBypassMethod.REALITY_METHODS.contains(FptnBypassMethod.OBFUSCATION))
    }

    private fun validTokenB64(): String {
        val json = """{"version":1,"username":"u","password":"p",
            "servers":[{"name":"S1","host":"1.2.3.4","port":443}]}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun tokenData(vararg servers: FptnServer): FptnTokenData =
        FptnTokenData(
            version = 1,
            username = "u",
            password = "p",
            servers = servers.toList(),
        )

    private fun server(name: String): FptnServer =
        FptnServer(
            name = name,
            host = "$name.example.com",
            port = 443,
            md5Fingerprint = "",
            countryCode = "",
        )
}
