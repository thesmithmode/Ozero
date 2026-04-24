package ru.ozero.enginexray

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.VlessServer
import kotlin.test.assertTrue

class XrayConfigBuilderTest {
    private val builder = XrayConfigBuilder()

    @Test
    fun buildsValidVlessRealityConfig() {
        val server =
            VlessServer(
                uuid = "11111111-2222-3333-4444-555555555555",
                host = "example.com",
                port = 443,
                security = "reality",
                fingerprint = "chrome",
                publicKey = "PUBKEY",
                shortId = "SHORT",
                sni = "google.com",
                transport = "xhttp",
                flow = "xtls-rprx-vision",
            )
        val json = builder.buildVless(server, socksPort = 10808)

        assertTrue(json.contains("\"protocol\":\"socks\""))
        assertTrue(json.contains("\"port\":10808"))
        assertTrue(json.contains("\"protocol\":\"vless\""))
        assertTrue(json.contains("example.com"))
        assertTrue(json.contains("11111111-2222-3333-4444-555555555555"))
        assertTrue(json.contains("\"serverName\":\"google.com\""))
        assertTrue(json.contains("\"fingerprint\":\"chrome\""))
        assertTrue(json.contains("\"publicKey\":\"PUBKEY\""))
        assertTrue(json.contains("\"shortId\":\"SHORT\""))
        assertTrue(json.contains("\"network\":\"xhttp\""))
        assertTrue(json.contains("\"flow\":\"xtls-rprx-vision\""))
    }

    @Test
    fun buildsTcpWithoutRealitySettings() {
        val server =
            VlessServer(
                uuid = "UUID",
                host = "host.io",
                port = 443,
                security = "none",
                transport = "tcp",
            )
        val json = builder.buildVless(server, socksPort = 10808)
        assertTrue(json.contains("\"security\":\"none\""))
        assertTrue(!json.contains("realitySettings"))
    }

    @Test
    fun escapesJsonSpecialChars() {
        val server =
            VlessServer(
                uuid = "UUID\"with\\quotes",
                host = "host.io",
                port = 443,
            )
        val json = builder.buildVless(server, socksPort = 10808)
        assertTrue(json.contains("UUID\\\"with\\\\quotes"))
    }
}
