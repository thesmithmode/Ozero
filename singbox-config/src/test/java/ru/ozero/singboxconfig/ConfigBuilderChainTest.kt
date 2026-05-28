package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.WireGuardOutboundConfig
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBuilderChainTest {

    private fun makeBean(
        uuid: String = "12345678-1234-1234-1234-123456789abc",
        host: String = "proxy.example.com",
        port: Int = 443,
    ) = VLESSBean().apply {
        this.uuid = uuid
        this.serverAddress = host
        this.serverPort = port
        this.type = "tcp"
        this.security = "none"
    }

    @Test
    fun `chain config uses SOCKS inbound instead of TUN`() {
        val json = ConfigBuilder.buildChainConfig(makeBean(), socksPort = 49408)

        assertContains(json, "\"type\":\"socks\"")
        assertContains(json, "\"tag\":\"socks-in\"")
        assertContains(json, "\"listen\":\"127.0.0.1\"")
        assertContains(json, "\"listen_port\":49408")
        assertFalse(json.contains("\"type\":\"tun\""), "chain config must not use TUN inbound")
    }

    @Test
    fun `chain config without upstream has no socks outbound`() {
        val json = ConfigBuilder.buildChainConfig(makeBean(), socksPort = 49408, upstream = null)

        assertFalse(json.contains("\"tag\":\"upstream\""), "no upstream when null")
    }

    @Test
    fun `chain config with upstream adds socks outbound and detour`() {
        val upstream = ConfigBuilder.Upstream("127.0.0.1", 49152)
        val json = ConfigBuilder.buildChainConfig(makeBean(), socksPort = 49408, upstream = upstream)

        assertContains(json, "\"tag\":\"upstream\"")
        assertContains(json, "\"server\":\"127.0.0.1\"")
        assertContains(json, "\"server_port\":49152")
        assertContains(json, "\"detour\":\"upstream\"")
    }

    @Test
    fun `chain config uses DoH DNS through proxy`() {
        val json = ConfigBuilder.buildChainConfig(makeBean(), socksPort = 49408)

        assertContains(json, "\"address\":\"https://1.1.1.1/dns-query\"")
        assertContains(json, "\"detour\":\"proxy\"")
        assertContains(json, "\"action\":\"hijack-dns\"")
        assertFalse(json.contains("\"type\":\"dns\""), "chain config must not rely on legacy dns outbound")
    }

    @Test
    fun `auto chain config with multiple beans produces urltest`() {
        val beans = listOf(
            makeBean(uuid = "aaaa-1", host = "s1.com"),
            makeBean(uuid = "aaaa-2", host = "s2.com"),
        )
        val json = ConfigBuilder.buildAutoChainConfig(beans, socksPort = 49410)

        assertContains(json, "\"type\":\"urltest\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"type\":\"socks\"")
        assertContains(json, "\"listen_port\":49410")
    }

    @Test
    fun `auto chain config filters unsupported transport beans`() {
        val supported = makeBean(uuid = "aaaa-1", host = "s1.com")
        val unsupported = makeBean(uuid = "aaaa-2", host = "s2.com").apply {
            type = "splithttp"
        }

        val json = ConfigBuilder.buildAutoChainConfig(listOf(supported, unsupported), socksPort = 49410)

        assertContains(json, "\"tag\":\"proxy-0\"")
        assertFalse(json.contains("splithttp"), "auto chain must not pass unsupported transports to libbox")
        assertFalse(json.contains("\"tag\":\"proxy-1\""), "unsupported beans must not leave gaps in urltest tags")
    }

    @Test
    fun `auto chain config fails fast when all transports unsupported`() {
        val unsupportedOnly = listOf(
            makeBean(uuid = "aaaa-2", host = "s2.com").apply { type = "splithttp" },
            makeBean(uuid = "aaaa-3", host = "s3.com").apply { type = "splithttp" },
        )

        val result = runCatching { ConfigBuilder.buildAutoChainConfig(unsupportedOnly, socksPort = 49410) }

        assertTrue(result.isFailure, "all unsupported transports must fail instead of building broken chain config")
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "supported transport")
    }

    @Test
    fun `auto chain config with upstream adds detour to all proxy outbounds`() {
        val beans = listOf(makeBean(uuid = "a1"), makeBean(uuid = "a2"))
        val upstream = ConfigBuilder.Upstream("127.0.0.1", 49200)
        val json = ConfigBuilder.buildAutoChainConfig(beans, socksPort = 49410, upstream = upstream)

        assertContains(json, "\"tag\":\"upstream\"")
        val detourCount = "\"detour\":\"upstream\"".toRegex().findAll(json).count()
        assertTrue(detourCount >= 2, "each proxy outbound should have detour, found $detourCount")
    }

    @Test
    fun `WireGuard chain config produces wireguard outbound`() {
        val wg = WireGuardOutboundConfig(
            privateKey = "testkey123=",
            peerPublicKey = "peerpub456=",
            serverHost = "engage.cloudflareclient.com",
            serverPort = 2408,
            localAddresses = listOf("172.16.0.2/32"),
            mtu = 1280,
            keepaliveSeconds = 25,
        )
        val json = ConfigBuilder.buildWireGuardChainConfig(wg, socksPort = 49420)

        assertContains(json, "\"type\":\"wireguard\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"server\":\"engage.cloudflareclient.com\"")
        assertContains(json, "\"server_port\":2408")
        assertContains(json, "\"private_key\":\"testkey123=\"")
        assertContains(json, "\"peer_public_key\":\"peerpub456=\"")
        assertContains(json, "\"mtu\":1280")
        assertContains(json, "\"persistent_keepalive_interval\":25")
        assertContains(json, "\"listen_port\":49420")
    }

    @Test
    fun `WireGuard chain config with upstream adds detour`() {
        val wg = WireGuardOutboundConfig(
            privateKey = "k=",
            peerPublicKey = "p=",
            serverHost = "1.2.3.4",
            serverPort = 51820,
            localAddresses = listOf("10.0.0.1/32"),
        )
        val upstream = ConfigBuilder.Upstream("127.0.0.1", 49300)
        val json = ConfigBuilder.buildWireGuardChainConfig(wg, socksPort = 49420, upstream = upstream)

        assertContains(json, "\"detour\":\"upstream\"")
        assertContains(json, "\"tag\":\"upstream\"")
    }
}
