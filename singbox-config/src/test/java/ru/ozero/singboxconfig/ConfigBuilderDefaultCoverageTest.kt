package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.WireGuardOutboundConfig
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBuilderDefaultCoverageTest {

    @Test
    fun `public builders exercise default probe and detour arguments`() {
        val ss = shadowsocks()
        val vless = vless("ws")
        val vmess = vmess("grpc")
        val trojan = trojan("http")

        val single = ConfigBuilder.buildSingboxConfig(ss)
        val auto = ConfigBuilder.buildSingboxAutoConfig(listOf(vless, vmess, trojan))
        val chain = ConfigBuilder.buildChainConfig(ss, 1080)
        val autoChain = ConfigBuilder.buildAutoChainConfig(listOf(vless, vmess), 1081)
        val profile = ConfigBuilder.buildProfileChainConfig(trojan, listOf(ss, vless))

        assertContains(single, "\"type\":\"shadowsocks\"")
        assertContains(auto, "\"type\":\"urltest\"")
        assertContains(chain, "\"listen_port\":1080")
        assertContains(autoChain, "\"listen_port\":1081")
        assertContains(profile, "\"tag\":\"chain-0\"")
    }

    @Test
    fun `chain builders include upstream detour when provided`() {
        val upstream = ConfigBuilder.Upstream("127.0.0.1", 19090)
        val ss = shadowsocks()
        val wg = WireGuardOutboundConfig(
            serverHost = "wg.example",
            serverPort = 51820,
            localAddresses = listOf("10.0.0.2/32"),
            privateKey = "private",
            peerPublicKey = "public",
            mtu = 1280,
            keepaliveSeconds = 25,
        )

        val chain = ConfigBuilder.buildChainConfig(ss, 1080, upstream)
        val auto = ConfigBuilder.buildAutoChainConfig(listOf(ss), 1081, upstream)
        val wireGuard = ConfigBuilder.buildWireGuardChainConfig(wg, 1082, upstream)

        assertContains(chain, "\"detour\":\"upstream\"")
        assertContains(auto, "\"detour\":\"upstream\"")
        assertContains(wireGuard, "\"persistent_keepalive_interval\":25")
        assertContains(wireGuard, "\"detour\":\"upstream\"")
        assertContains(wireGuard, "\"type\":\"socks\",\"tag\":\"upstream\"")
    }

    @Test
    fun `transports tls and shadowsocks plugin options are rendered`() {
        val ws = vless("ws").apply {
            host = "cdn.example"
            path = "/ws"
            maxEarlyData = 2048
            earlyDataHeaderName = "Sec-WebSocket-Protocol"
            security = "tls"
            sni = "sni.example"
            alpn = "h2,http/1.1"
            utlsFingerprint = "chrome"
            allowInsecure = true
            certificates = "pem"
        }
        val grpcReality = vless("grpc").apply {
            grpcServiceName = "svc"
            security = "reality"
            realityPublicKey = "pub"
            realityShortId = "sid"
        }
        val ss = shadowsocks().apply {
            plugin = "obfs-local"
            pluginOpts = "obfs=http;obfs-host=edge.example"
        }

        val wsJson = ConfigBuilder.buildSingboxConfig(ws)
        val grpcJson = ConfigBuilder.buildSingboxConfig(grpcReality)
        val ssJson = ConfigBuilder.buildSingboxConfig(ss)

        assertContains(wsJson, "\"max_early_data\":2048")
        assertContains(wsJson, "\"early_data_header_name\":\"Sec-WebSocket-Protocol\"")
        assertContains(wsJson, "\"insecure\":true")
        assertContains(wsJson, "\"certificate\":\"pem\"")
        assertContains(grpcJson, "\"type\":\"grpc\"")
        assertContains(grpcJson, "\"reality\":{\"enabled\":true")
        assertContains(ssJson, "\"plugin_opts\":\"obfs=http;obfs-host=edge.example\"")
    }

    @Test
    fun `unsupported selected profile and empty auto chain fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildAutoChainConfig(emptyList(), 1080)
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProfileChainConfig(vless("unsupported"), emptyList())
        }
        assertFalse(ConfigBuilder.isSupportedBean(vless("quic")))
        assertTrue(ConfigBuilder.isSupportedBean(shadowsocks()))
    }

    private fun shadowsocks() = ShadowsocksBean().apply {
        serverAddress = "ss.example"
        serverPort = 8388
        method = "chacha20-ietf-poly1305"
        password = "password"
    }

    private fun vless(type: String) = VLESSBean().apply {
        serverAddress = "vless.example"
        serverPort = 443
        uuid = "00000000-0000-0000-0000-000000000001"
        this.type = type
    }

    private fun vmess(type: String) = VMessBean().apply {
        serverAddress = "vmess.example"
        serverPort = 443
        uuid = "00000000-0000-0000-0000-000000000002"
        this.type = type
    }

    private fun trojan(type: String) = TrojanBean().apply {
        serverAddress = "trojan.example"
        serverPort = 443
        password = "password"
        this.type = type
    }
}
