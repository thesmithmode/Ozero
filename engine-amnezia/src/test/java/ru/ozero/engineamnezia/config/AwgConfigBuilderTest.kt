package ru.ozero.engineamnezia.config

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.AmneziaWgServer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwgConfigBuilderTest {

    private val builder = AwgConfigBuilder()

    private fun sample() = AmneziaWgServer(
        privateKey = "PRIV",
        publicKey = "PUB",
        host = "vpn.example.com",
        port = 51820,
        addresses = listOf("10.0.0.2/32"),
        dns = listOf("1.1.1.1", "8.8.8.8"),
        mtu = 1280,
        persistentKeepalive = 25,
        jc = 4,
        jmin = 40,
        jmax = 70,
        s1 = 0,
        s2 = 0,
        h1 = 1,
        h2 = 2,
        h3 = 3,
        h4 = 4,
    )

    @Test
    fun rendersInterfaceAndPeerSections() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("[Interface]"))
        assertTrue(cfg.contains("[Peer]"))
        assertTrue(cfg.contains("PrivateKey = PRIV"))
        assertTrue(cfg.contains("PublicKey = PUB"))
    }

    @Test
    fun rendersEndpointHostAndPort() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("Endpoint = vpn.example.com:51820"))
    }

    @Test
    fun rendersAddressDnsMtu() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("Address = 10.0.0.2/32"))
        assertTrue(cfg.contains("DNS = 1.1.1.1, 8.8.8.8"))
        assertTrue(cfg.contains("MTU = 1280"))
    }

    @Test
    fun rendersAllowedIpsCsv() {
        val cfg = builder.build(sample().copy(allowedIps = listOf("0.0.0.0/0", "::/0")))
        assertTrue(cfg.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
    }

    @Test
    fun rendersJunkPacketParams() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("Jc = 4"))
        assertTrue(cfg.contains("Jmin = 40"))
        assertTrue(cfg.contains("Jmax = 70"))
    }

    @Test
    fun rendersAllH1ToH4() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("H1 = 1"))
        assertTrue(cfg.contains("H2 = 2"))
        assertTrue(cfg.contains("H3 = 3"))
        assertTrue(cfg.contains("H4 = 4"))
    }

    @Test
    fun rendersS1S2WhenNonZero() {
        val cfg = builder.build(sample().copy(s1 = 50, s2 = 80))
        assertTrue(cfg.contains("S1 = 50"))
        assertTrue(cfg.contains("S2 = 80"))
    }

    @Test
    fun omitsObfuscationFieldsWhenAllZero() {
        val plain = sample().copy(jc = 0, jmin = 0, jmax = 0, s1 = 0, s2 = 0, h1 = 0, h2 = 0, h3 = 0, h4 = 0)
        val cfg = builder.build(plain)
        assertFalse(cfg.contains("Jc"))
        assertFalse(cfg.contains("Jmin"))
        assertFalse(cfg.contains("Jmax"))
        assertFalse(cfg.contains("S1"))
        assertFalse(cfg.contains("S2"))
        assertFalse(cfg.contains("H1"))
        assertFalse(cfg.contains("H4"))
    }

    @Test
    fun rendersPresharedKeyWhenPresent() {
        val cfg = builder.build(sample().copy(presharedKey = "PSK"))
        assertTrue(cfg.contains("PresharedKey = PSK"))
    }

    @Test
    fun omitsPresharedKeyWhenAbsent() {
        val cfg = builder.build(sample().copy(presharedKey = null))
        assertFalse(cfg.contains("PresharedKey"))
    }

    @Test
    fun rendersKeepaliveWhenPositive() {
        val cfg = builder.build(sample())
        assertTrue(cfg.contains("PersistentKeepalive = 25"))
    }

    @Test
    fun omitsKeepaliveWhenZero() {
        val cfg = builder.build(sample().copy(persistentKeepalive = 0))
        assertFalse(cfg.contains("PersistentKeepalive"))
    }

    @Test
    fun rejectsBlankPrivateKey() {
        val ex = runCatching { builder.build(sample().copy(privateKey = "")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankPublicKey() {
        val ex = runCatching { builder.build(sample().copy(publicKey = "  ")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsJminGreaterThanJmax() {
        val ex = runCatching { builder.build(sample().copy(jmin = 100, jmax = 50)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsCollidingMagicHeaders() {
        val ex = runCatching {
            builder.build(sample().copy(h1 = 5, h2 = 5, h3 = 6, h4 = 7))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsPartialMagicHeaders() {
        val ex = runCatching {
            // h1 задан, остальные = 0 — нарушение полноты
            builder.build(sample().copy(h1 = 1, h2 = 0, h3 = 0, h4 = 0))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun deterministicOutput() {
        val a = builder.build(sample())
        val b = builder.build(sample())
        assertEquals(a, b)
    }
}
