package ru.ozero.enginenaive.config

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.NaiveServer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaiveConfigBuilderTest {
    private val builder = NaiveConfigBuilder()

    private fun server() = NaiveServer(
        scheme = "https",
        username = "u",
        password = "p",
        host = "naive.example.com",
        port = 443,
        remark = null,
    )

    @Test
    fun rendersListenSocksOnLoopback() {
        val cfg = builder.build(server(), 1080)
        assertTrue(cfg.contains("\"listen\":\"socks://127.0.0.1:1080\""))
    }

    @Test
    fun rendersProxyUrl() {
        val cfg = builder.build(server(), 1080)
        assertTrue(cfg.contains("\"proxy\":\"https://u:p@naive.example.com:443\""))
    }

    @Test
    fun rendersLogEmpty() {
        val cfg = builder.build(server(), 1080)
        assertTrue(cfg.contains("\"log\":\"\""))
    }

    @Test
    fun rejectsInvalidPort() {
        val ex = runCatching { builder.build(server(), 0) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankHost() {
        val ex = runCatching { builder.build(server().copy(host = ""), 1080) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankCreds() {
        val ex1 = runCatching { builder.build(server().copy(username = ""), 1080) }.exceptionOrNull()
        val ex2 = runCatching { builder.build(server().copy(password = ""), 1080) }.exceptionOrNull()
        assertTrue(ex1 is IllegalArgumentException)
        assertTrue(ex2 is IllegalArgumentException)
    }

    @Test
    fun deterministicOutput() {
        val a = builder.build(server(), 1080)
        val b = builder.build(server(), 1080)
        assertEquals(a, b)
    }

    @Test
    fun supportsQuicScheme() {
        val cfg = builder.build(server().copy(scheme = "quic"), 1080)
        assertTrue(cfg.contains("\"proxy\":\"quic://u:p@naive.example.com:443\""))
    }
}
