package ru.ozero.enginetor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.enginetor.bridges.TorBridge
import ru.ozero.enginetor.config.TorBuildOptions
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TorEngineTest {
    private lateinit var delegate: LibTorDelegate

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
    }

    private fun engine(
        bridges: List<TorBridge> = emptyList(),
        options: TorBuildOptions = TorBuildOptions(socksPort = 9050, controlPort = 9051, dataDir = "/tmp/tor"),
    ) = TorEngine(delegate, bridges = bridges, buildOptions = options)

    @Test fun engineIdIsTor() = assertEquals(EngineId.TOR, engine().id)

    @Test
    fun startRequiresTorConfig() = runTest {
        val ex = runCatching { engine().start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startPassesTorrcToDelegate() = runTest {
        every { delegate.startTor(any()) } returns 0
        val cfg = slot<String>()
        every { delegate.startTor(capture(cfg)) } returns 0
        engine().start(EngineConfig.Tor(socksPort = 9050))
        assertTrue(cfg.captured.contains("SocksPort 127.0.0.1:9050"))
    }

    @Test
    fun startSuccessWhenDelegateReturnsZero() = runTest {
        every { delegate.startTor(any()) } returns 0
        val r = engine().start(EngineConfig.Tor(socksPort = 9050))
        assertIs<StartResult.Success>(r)
    }

    @Test
    fun startFailureWhenDelegateNonZero() = runTest {
        every { delegate.startTor(any()) } returns -7
        val r = engine().start(EngineConfig.Tor(socksPort = 9050))
        assertIs<StartResult.Failure>(r)
        assertTrue(r.reason.contains("-7"))
    }

    @Test
    fun stopCallsDelegate() = runTest {
        every { delegate.startTor(any()) } returns 0
        val e = engine()
        e.start(EngineConfig.Tor(socksPort = 9050))
        e.stop()
        verify { delegate.stopTor() }
    }

    @Test
    fun probeFailsWhenNotStarted() = runTest {
        assertIs<ProbeResult.Failure>(engine().probe())
    }

    @Test
    fun probeFailsWhenNotBootstrapped() = runTest {
        every { delegate.startTor(any()) } returns 0
        every { delegate.isBootstrapped() } returns false
        every { delegate.bootstrapPercent() } returns 25
        val e = engine()
        e.start(EngineConfig.Tor(socksPort = 9050))
        val r = e.probe()
        assertIs<ProbeResult.Failure>(r)
        assertTrue(r.reason.contains("25"))
    }

    @Test
    fun probeSuccessWhenBootstrappedAndSocketListens() = runTest {
        val server = ServerSocket(0)
        server.acceptSocks5InBackground()
        try {
            val port = server.localPort
            every { delegate.startTor(any()) } returns 0
            every { delegate.isBootstrapped() } returns true
            val e = engine(options = TorBuildOptions(socksPort = port, controlPort = port + 1, dataDir = "/tmp/tor"))
            e.start(EngineConfig.Tor(socksPort = port))
            assertIs<ProbeResult.Success>(e.probe())
        } finally {
            server.close()
        }
    }

    private fun ServerSocket.acceptSocks5InBackground() {
        thread(isDaemon = true) {
            runCatching {
                accept().use { c ->
                    c.getInputStream().read(ByteArray(8))
                    c.getOutputStream().write(byteArrayOf(0x05, 0x00))
                    c.getOutputStream().flush()
                }
            }
        }
    }

    @Test
    fun startBuildsConfigWithBridges() = runTest {
        val bridge = TorBridge(
            transport = "obfs4",
            address = "1.1.1.1:443",
            fingerprint = "FP",
            args = sortedMapOf("cert" to "C", "iat-mode" to "0"),
        )
        every { delegate.startTor(any()) } returns 0
        val cfg = slot<String>()
        every { delegate.startTor(capture(cfg)) } returns 0
        TorEngine(
            delegate,
            bridges = listOf(bridge),
            buildOptions = TorBuildOptions(
                socksPort = 9050, controlPort = 9051, dataDir = "/tmp/tor",
                ptBinaries = mapOf("obfs4" to "/lib/obfs4proxy"),
            ),
        ).start(EngineConfig.Tor(socksPort = 9050))
        assertTrue(cfg.captured.contains("UseBridges 1"))
        assertTrue(cfg.captured.contains("Bridge obfs4 1.1.1.1:443 FP cert=C iat-mode=0"))
    }

    @Test
    fun startUsesDefaultBridgesWhenUserBridgesEmpty() = runTest {
        val defaultBridge = TorBridge(
            transport = "obfs4",
            address = "9.9.9.9:443",
            fingerprint = "DEFAULT",
            args = sortedMapOf("cert" to "DC"),
        )
        every { delegate.startTor(any()) } returns 0
        val cfg = slot<String>()
        every { delegate.startTor(capture(cfg)) } returns 0
        TorEngine(
            delegate,
            bridges = emptyList(),
            buildOptions = TorBuildOptions(
                socksPort = 9050, controlPort = 9051, dataDir = "/tmp/tor",
                ptBinaries = mapOf("obfs4" to "/lib/obfs4proxy"),
            ),
            defaultBridges = listOf(defaultBridge),
        ).start(EngineConfig.Tor(socksPort = 9050))
        assertTrue(
            cfg.captured.contains("Bridge obfs4 9.9.9.9:443 DEFAULT"),
            "когда user bridges пуст, должны подставиться defaultBridges из assets — " +
                "иначе Tor в РФ не подключится (direct DPI блок).",
        )
    }

    @Test
    fun `start prioritises user bridges over default когда оба заданы`() = runTest {
        val userBridge = TorBridge(
            transport = "obfs4", address = "1.1.1.1:443", fingerprint = "USER",
        )
        val defaultBridge = TorBridge(
            transport = "obfs4", address = "9.9.9.9:443", fingerprint = "DEFAULT",
        )
        every { delegate.startTor(any()) } returns 0
        val cfg = slot<String>()
        every { delegate.startTor(capture(cfg)) } returns 0
        TorEngine(
            delegate,
            bridges = listOf(userBridge),
            buildOptions = TorBuildOptions(
                socksPort = 9050, controlPort = 9051, dataDir = "/tmp/tor",
                ptBinaries = mapOf("obfs4" to "/lib/obfs4proxy"),
            ),
            defaultBridges = listOf(defaultBridge),
        ).start(EngineConfig.Tor(socksPort = 9050))
        assertTrue(cfg.captured.contains("USER"), "user bridge должен оказаться в torrc")
        assertTrue(!cfg.captured.contains("DEFAULT"), "default не должен mixать-ся когда user задал свой")
    }
}
