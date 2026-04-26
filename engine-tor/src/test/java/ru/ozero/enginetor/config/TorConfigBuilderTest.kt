package ru.ozero.enginetor.config

import org.junit.jupiter.api.Test
import ru.ozero.enginetor.bridges.TorBridge
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorConfigBuilderTest {
    private val builder = TorConfigBuilder()

    private fun opts(ptBinaries: Map<String, String> = emptyMap()) =
        TorBuildOptions(dataDir = "/data/data/ru.ozero.app/files/tor", ptBinaries = ptBinaries)

    private fun obfs4() = TorBridge(
        transport = "obfs4",
        address = "1.1.1.1:443",
        fingerprint = "FP1",
        args = sortedMapOf("cert" to "C1", "iat-mode" to "0"),
    )

    private fun snowflake() = TorBridge(
        transport = "snowflake",
        address = "snowflake.torproject.org:80",
        fingerprint = "FP2",
        args = sortedMapOf("url" to "https://broker.example.com/", "front" to "cdn.example.com"),
    )

    @Test
    fun rendersBaseFields() {
        val cfg = builder.build(emptyList(), opts())
        assertTrue(cfg.contains("SocksPort 127.0.0.1:9050"))
        assertTrue(cfg.contains("ControlPort 127.0.0.1:9051"))
        assertTrue(cfg.contains("DataDirectory /data/data/ru.ozero.app/files/tor"))
    }

    @Test
    fun socksAndControlPortsBindToLocalhost() {
        val cfg = builder.build(emptyList(), opts())
        // ControlPort на 0.0.0.0 = любое приложение в той же сети может NEWNYM
        assertFalse(cfg.contains("ControlPort 9051\n"))
        assertFalse(cfg.contains("SocksPort 9050\n"))
        assertTrue(cfg.contains("ControlPort 127.0.0.1:9051"))
    }

    @Test
    fun excludesRuByExitNodesByDefault() {
        val cfg = builder.build(emptyList(), opts())
        assertTrue(cfg.contains("ExcludeExitNodes {ru},{by}"))
        assertTrue(cfg.contains("StrictNodes 1"))
    }

    @Test
    fun usesBridgesWhenProvided() {
        val cfg = builder.build(listOf(obfs4()), opts(mapOf("obfs4" to "/lib/obfs4proxy")))
        assertTrue(cfg.contains("UseBridges 1"))
        assertTrue(cfg.contains("ClientTransportPlugin obfs4 exec /lib/obfs4proxy"))
        assertTrue(cfg.contains("Bridge obfs4 1.1.1.1:443 FP1 cert=C1 iat-mode=0"))
    }

    @Test
    fun rendersMultipleTransports() {
        val cfg = builder.build(
            listOf(obfs4(), snowflake()),
            opts(mapOf("obfs4" to "/lib/obfs4proxy", "snowflake" to "/lib/snowflake")),
        )
        assertTrue(cfg.contains("ClientTransportPlugin obfs4 exec /lib/obfs4proxy"))
        assertTrue(cfg.contains("ClientTransportPlugin snowflake exec /lib/snowflake"))
        assertTrue(cfg.contains("Bridge obfs4"))
        assertTrue(cfg.contains("Bridge snowflake"))
    }

    @Test
    fun omitsBridgeFieldsWhenEmpty() {
        val cfg = builder.build(emptyList(), opts())
        assertFalse(cfg.contains("UseBridges"))
        assertFalse(cfg.contains("ClientTransportPlugin"))
    }

    @Test
    fun rejectsMissingPtBinaryForUsedTransport() {
        val ex = runCatching { builder.build(listOf(obfs4()), opts()) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsSocksEqControlPort() {
        val ex = runCatching {
            builder.build(emptyList(), opts().copy(socksPort = 9050, controlPort = 9050))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankDataDir() {
        val ex = runCatching {
            builder.build(emptyList(), TorBuildOptions(dataDir = ""))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsPtBinaryWithNewline() {
        val ex = runCatching {
            builder.build(listOf(obfs4()), opts(mapOf("obfs4" to "/lib/obfs4proxy\nMaliciousLine")))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun deterministicOutput() {
        val a = builder.build(listOf(obfs4()), opts(mapOf("obfs4" to "/p")))
        val b = builder.build(listOf(obfs4()), opts(mapOf("obfs4" to "/p")))
        assertEquals(a, b)
    }
}
