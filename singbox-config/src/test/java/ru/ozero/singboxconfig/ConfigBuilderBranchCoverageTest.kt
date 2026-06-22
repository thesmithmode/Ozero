package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.WireGuardOutboundConfig
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@Suppress("LongMethod")
class ConfigBuilderBranchCoverageTest {
    private val validRealityPublicKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"

    @Test
    fun `full config adds probe socks inbound only for positive port`() {
        val withoutProbe = ConfigBuilder.buildSingboxConfig(vless(), probeSocksPort = 0)
        val withProbe = ConfigBuilder.buildSingboxConfig(vless(), probeSocksPort = 2080)

        assertFalse(withoutProbe.contains("\"listen_port\":0"))
        assertContains(withProbe, "\"listen_port\":2080")
    }

    @Test
    fun `tls block covers insecure certificate and server name fallbacks`() {
        val bean = vless().apply {
            security = "tls"
            serverAddress = "203.0.113.10"
            host = "front.example.com"
            allowInsecure = true
            certificates = "-----BEGIN CERTIFICATE-----"
        }

        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertFalse(json.contains("\"server_name\""))
        assertContains(json, "\"insecure\":true")
        assertContains(json, "\"certificate\":\"-----BEGIN CERTIFICATE-----\"")
    }

    @Test
    fun `tls block uses explicit sni before server and never transport host`() {
        val json = ConfigBuilder.buildSingboxConfig(
            vless().apply {
                security = "tls"
                serverAddress = "server.example.com"
                host = "front.example.com"
                sni = "sni.example.com"
            },
        )

        assertContains(json, "\"server_name\":\"sni.example.com\"")
        assertFalse(json.contains("\"server_name\":\"front.example.com\""))
    }

    @Test
    fun `tls block falls back to domain server address not websocket host`() {
        val json = ConfigBuilder.buildSingboxConfig(
            vless().apply {
                security = "tls"
                serverAddress = "server.example.com"
                host = "front.example.com"
            },
        )

        assertContains(json, "\"server_name\":\"server.example.com\"")
        assertFalse(json.contains("\"server_name\":\"front.example.com\""))
    }

    @Test
    fun `httpupgrade transport omits empty fields and emits host when present`() {
        val empty = ConfigBuilder.buildSingboxConfig(vless().apply { type = "httpupgrade" })
        val filled = ConfigBuilder.buildSingboxConfig(
            vless().apply {
                type = "httpupgrade"
                host = "front.example.com"
                path = "/upgrade"
            },
        )

        assertContains(empty, "\"type\":\"httpupgrade\"")
        assertContains(empty, "\"path\":\"/\"")
        assertFalse(empty.contains("\"host\""))
        assertContains(filled, "\"host\":\"front.example.com\"")
    }

    @Test
    fun `profile chain wrappers detour selected through last wrapper`() {
        val json = ConfigBuilder.buildProfileChainConfig(
            selected = vless(uuid = "selected"),
            wrappers = listOf(vmess(), trojan(), shadowsocks()),
            probeSocksPort = 2081,
        )

        assertContains(json, "\"tag\":\"chain-0\"")
        assertContains(json, "\"tag\":\"chain-1\"")
        assertContains(json, "\"tag\":\"chain-2\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"detour\":\"chain-2\"")
        assertContains(json, "\"listen_port\":2081")
    }

    @Test
    fun `profile chain rejects unsupported selected transport but filters unsupported wrappers`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProfileChainConfig(vless().apply { type = "splithttp" }, wrappers = emptyList())
        }

        val json = ConfigBuilder.buildProfileChainProxyConfig(
            selected = vless(),
            wrappers = listOf(vless(uuid = "unsupported").apply { type = "splithttp" }, vmess()),
            socksPort = 2082,
        )

        assertFalse(json.contains("unsupported"))
        assertContains(json, "\"listen_port\":2082")
        assertContains(json, "\"detour\":\"chain-0\"")
    }

    @Test
    fun `auto config rejects empty and all unsupported beans`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildSingboxAutoConfig(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildSingboxAutoConfig(listOf(vless().apply { type = "splithttp" }))
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildAutoChainConfig(emptyList(), socksPort = 2083)
        }
    }

    @Test
    fun `wireguard outbound omits optional keepalive and upstream detour when absent`() {
        val json = ConfigBuilder.buildWireGuardChainConfig(
            WireGuardOutboundConfig(
                privateKey = "private",
                peerPublicKey = "peer",
                serverHost = "203.0.113.1",
                serverPort = 51820,
                localAddresses = listOf("10.0.0.2/32", "fd00::2/128"),
                mtu = 1280,
                keepaliveSeconds = 0,
            ),
            socksPort = 2084,
        )

        assertContains(json, "\"local_address\":[\"10.0.0.2/32\",\"fd00::2/128\"]")
        assertFalse(json.contains("persistent_keepalive_interval"))
        assertFalse(json.contains("\"detour\":\"upstream\""))
    }

    @Test
    fun `wireguard outbound emits keepalive and upstream detour when present`() {
        val json = ConfigBuilder.buildWireGuardChainConfig(
            WireGuardOutboundConfig(
                privateKey = "private",
                peerPublicKey = "peer",
                serverHost = "203.0.113.1",
                serverPort = 51820,
                localAddresses = listOf("10.0.0.2/32"),
                mtu = 1280,
                keepaliveSeconds = 25,
            ),
            socksPort = 2084,
            upstream = ConfigBuilder.Upstream("127.0.0.1", 1080),
        )

        assertContains(json, "\"persistent_keepalive_interval\":25")
        assertContains(json, "\"detour\":\"upstream\"")
    }

    @Test
    fun `tls reality and transport variants keep branch coverage honest`() {
        val reality = vless().apply {
            security = "reality"
            host = "front.example.com"
            sni = ""
            alpn = "h2,http/1.1"
            realityPublicKey = validRealityPublicKey
            realityShortId = "01"
            realityFingerprint = ""
        }
        val ws = vless().apply {
            type = "ws"
            path = "/ws"
            host = "ws.example.com"
            earlyDataHeaderName = "16"
            maxEarlyData = 0
        }
        val grpc = vless().apply {
            type = "grpc"
            grpcServiceName = "svc"
        }

        val realityJson = ConfigBuilder.buildSingboxConfig(reality)
        val wsJson = ConfigBuilder.buildSingboxConfig(ws, probeSocksPort = 0)
        val grpcJson = ConfigBuilder.buildSingboxConfig(grpc)

        assertContains(realityJson, "\"server_name\":\"front.example.com\"")
        assertContains(realityJson, "\"public_key\":\"$validRealityPublicKey\"")
        assertContains(realityJson, "\"short_id\":\"01\"")
        assertContains(realityJson, "\"fingerprint\":\"chrome\"")
        assertContains(wsJson, "\"type\":\"ws\"")
        assertContains(wsJson, "\"headers\":{\"Host\":\"ws.example.com\"}")
        assertContains(wsJson, "\"max_early_data\":16")
        assertFalse(wsJson.contains("early_data_header_name"))
        assertContains(grpcJson, "\"type\":\"grpc\"")
        assertContains(grpcJson, "\"service_name\":\"svc\"")
    }

    @Test
    fun `http h2 and unsupported bean branches stay covered`() {
        val http = vless().apply {
            type = "http"
            host = "front.example.com"
            path = ""
        }
        val h2 = vless().apply {
            type = "h2"
            host = ""
            path = "/h2"
        }
        val unknown = vless().apply {
            type = "unknown"
        }
        val unsupported = object : AbstractBean() {}

        val httpJson = ConfigBuilder.buildSingboxConfig(http)
        val h2Json = ConfigBuilder.buildSingboxConfig(h2)
        val unknownJson = ConfigBuilder.buildChainConfig(unknown, socksPort = 2085)

        assertContains(httpJson, "\"type\":\"http\"")
        assertContains(httpJson, "\"host\":[\"front.example.com\"]")
        assertContains(httpJson, "\"path\":\"/\"")
        assertContains(h2Json, "\"type\":\"http\"")
        assertFalse(h2Json.contains("\"host\""))
        assertContains(h2Json, "\"path\":\"/h2\"")
        assertFalse(unknownJson.contains("\"transport\""))
        assertFalse(unknownJson.contains("\"tls\""))
        assertContains(unknownJson, "\"listen_port\":2085")
        assertContains(unknownJson, "\"final\":\"proxy\"")
        assertFailsWith<IllegalStateException> {
            ConfigBuilder.buildChainConfig(unsupported, socksPort = 2086)
        }
    }

    @Test
    fun `tls branch escapes special characters and emits optional knobs`() {
        val bean = vless().apply {
            security = "tls"
            serverAddress = "srv.example.com"
            sni = "srv\n\"quoted\"\\\u0001.example.com"
            host = "front.example.com,other.example.com"
            alpn = "h2, http/1.1"
            utlsFingerprint = "firefox"
            allowInsecure = true
            certificates = "-----BEGIN CERTIFICATE-----"
        }

        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"server_name\":\"srv\\n\\\"quoted\\\"\\\\\\u0001.example.com\"")
        assertContains(json, "\"alpn\":[\"h2\",\"http/1.1\"]")
        assertContains(json, "\"utls\":{\"enabled\":true,\"fingerprint\":\"firefox\"}")
        assertContains(json, "\"insecure\":true")
        assertContains(json, "\"certificate\":\"-----BEGIN CERTIFICATE-----\"")
    }

    @Test
    fun `shadowsocks omits plugin fields when they are blank`() {
        val bean = shadowsocks().apply {
            plugin = ""
            pluginOpts = ""
        }

        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertFalse(json.contains("\"plugin\""))
        assertFalse(json.contains("\"plugin_opts\""))
    }

    @Test
    fun `default outbound branches omit transport tls detour and probe socks`() {
        val vmess = vmess().apply {
            type = "tcp"
            security = "none"
            encryption = ""
        }
        val trojan = trojan().apply {
            type = "tcp"
            security = "none"
        }
        val shadowsocks = shadowsocks().apply {
            plugin = "v2ray-plugin"
            pluginOpts = "mode=websocket"
        }
        val vmessJson = ConfigBuilder.buildSingboxConfig(vmess)
        val trojanJson = ConfigBuilder.buildSingboxConfig(trojan)
        val shadowsocksJson = ConfigBuilder.buildChainConfig(
            bean = shadowsocks,
            socksPort = 2091,
            upstream = ConfigBuilder.Upstream("127.0.0.1", 1080),
        )

        assertContains(vmessJson, "\"type\":\"vmess\"")
        assertContains(vmessJson, "\"security\":\"auto\"")
        assertFalse(vmessJson.contains("\"transport\""))
        assertFalse(vmessJson.contains("\"tls\""))
        assertFalse(vmessJson.contains("\"listen_port\":0"))
        assertContains(trojanJson, "\"type\":\"trojan\"")
        assertFalse(trojanJson.contains("\"packet_encoding\""))
        assertFalse(trojanJson.contains("\"tls\""))
        assertContains(shadowsocksJson, "\"plugin\":\"v2ray-plugin\"")
        assertContains(shadowsocksJson, "\"plugin_opts\":\"mode=websocket\"")
        assertContains(shadowsocksJson, "\"detour\":\"upstream\"")
    }

    @Test
    fun `json string escapes carriage return tab backspace and form feed`() {
        val bean = vless().apply {
            serverAddress = "srv\r\t\b${12.toChar()}.example.com"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, """srv\r\t\b\f.example.com""")
    }

    @Test
    fun `vless flow normalization keeps vision variants only`() {
        val blank = ConfigBuilder.buildSingboxConfig(vless().apply { flow = " " })
        val exact = ConfigBuilder.buildSingboxConfig(vless().apply { flow = "xtls-rprx-vision" })
        val suffixed = ConfigBuilder.buildSingboxConfig(vless().apply { flow = "xtls-rprx-vision-udp443" })
        val unsupported = ConfigBuilder.buildSingboxConfig(vless().apply { flow = "unknown-flow" })

        assertFalse(blank.contains("\"flow\""))
        assertContains(exact, "\"flow\":\"xtls-rprx-vision\"")
        assertContains(suffixed, "\"flow\":\"xtls-rprx-vision\"")
        assertFalse(unsupported.contains("\"flow\""))
    }

    @Test
    fun `auto chain config emits upstream detour for every supported proxy`() {
        val json = ConfigBuilder.buildAutoChainConfig(
            beans = listOf(vless(), vmess(), vless(uuid = "unsupported").apply { type = "splithttp" }),
            socksPort = 2090,
            upstream = ConfigBuilder.Upstream("127.0.0.1", 1080),
        )

        assertContains(json, "\"tag\":\"upstream\"")
        assertContains(json, "\"detour\":\"upstream\"")
        assertContains(json, "\"listen_port\":2090")
        assertFalse(json.contains("unsupported"))
    }

    @Test
    fun `profile chain without wrappers keeps selected outbound direct`() {
        val json = ConfigBuilder.buildProfileChainConfig(
            selected = vless(uuid = "selected-only"),
            wrappers = emptyList(),
        )

        assertContains(json, "\"tag\":\"proxy\"")
        assertFalse(json.contains("\"tag\":\"chain-0\""))
        assertFalse(json.contains("\"detour\":\"chain-"))
    }

    @Test
    fun `vmess and trojan emit transport and tls optional branches`() {
        val vmess = vmess().apply {
            type = "ws"
            path = ""
            host = ""
            earlyDataHeaderName = "Sec-WebSocket-Protocol"
            maxEarlyData = 32
            security = "tls"
            sni = "vmess.example.com"
            allowInsecure = false
            utlsFingerprint = ""
        }
        val trojan = trojan().apply {
            type = "grpc"
            grpcServiceName = "trojan-service"
            security = "tls"
            host = "front.example.com;other.example.com"
            sni = ""
        }
        val vmessJson = ConfigBuilder.buildSingboxConfig(vmess)
        val trojanJson = ConfigBuilder.buildSingboxConfig(trojan)

        assertContains(vmessJson, "\"type\":\"ws\"")
        assertContains(vmessJson, "\"path\":\"/\"")
        assertContains(vmessJson, "\"max_early_data\":32")
        assertContains(vmessJson, "\"early_data_header_name\":\"Sec-WebSocket-Protocol\"")
        assertContains(vmessJson, "\"tls\":{\"enabled\":true")
        assertFalse(vmessJson.contains("\"headers\""))
        assertFalse(vmessJson.contains("\"insecure\""))
        assertContains(trojanJson, "\"type\":\"grpc\"")
        assertContains(trojanJson, "\"service_name\":\"trojan-service\"")
        assertContains(trojanJson, "\"server_name\":\"trojan.example.com\"")
        assertContains(trojanJson, "\"tls\":{\"enabled\":true")
    }

    @Test
    fun `empty security and plugin option edges omit optional blocks`() {
        val emptySecurity = vless().apply {
            security = ""
            host = ""
        }
        val pluginOptsWithoutPlugin = shadowsocks().apply {
            plugin = ""
            pluginOpts = "mode=websocket"
        }
        val emptySecurityJson = ConfigBuilder.buildSingboxConfig(emptySecurity)
        val pluginOptsWithoutPluginJson = ConfigBuilder.buildSingboxConfig(pluginOptsWithoutPlugin)

        assertFalse(emptySecurityJson.contains("\"tls\""))
        assertFalse(pluginOptsWithoutPluginJson.contains("\"plugin\""))
        assertFalse(pluginOptsWithoutPluginJson.contains("\"plugin_opts\""))
    }

    @Test
    fun `warp adapter covers cidr preservation ipv6 default suffix and mtu clamp`() {
        val lowMtu = WarpToWireGuardAdapter.convert(
            privateKey = "private",
            peerPublicKey = "peer",
            peerEndpoint = "[2001:db8::1]:51820",
            interfaceAddressV4 = "10.0.0.2/24",
            interfaceAddressV6 = "fd00::2",
            mtu = 1000,
            keepaliveSeconds = 25,
        )
        val highMtu = WarpToWireGuardAdapter.convert(
            privateKey = "private",
            peerPublicKey = "peer",
            peerEndpoint = "203.0.113.1:51820",
            interfaceAddressV4 = "",
            interfaceAddressV6 = "fd00::3/64",
            mtu = 2000,
            keepaliveSeconds = 0,
        )

        assertEquals("2001:db8::1", lowMtu.serverHost)
        assertEquals(listOf("10.0.0.2/24", "fd00::2/128"), lowMtu.localAddresses)
        assertEquals(1280, lowMtu.mtu)
        assertEquals(listOf("fd00::3/64"), highMtu.localAddresses)
        assertEquals(1500, highMtu.mtu)
    }

    private fun vless(uuid: String = "12345678-1234-1234-1234-123456789abc") = VLESSBean().apply {
        this.uuid = uuid
        serverAddress = "server.example.com"
        serverPort = 443
        type = "tcp"
        security = "none"
    }

    private fun vmess() = VMessBean().apply {
        uuid = "vmess"
        serverAddress = "vmess.example.com"
        serverPort = 443
        type = "tcp"
    }

    private fun trojan() = TrojanBean().apply {
        password = "secret"
        serverAddress = "trojan.example.com"
        serverPort = 443
        type = "tcp"
    }

    private fun shadowsocks() = ShadowsocksBean().apply {
        method = "aes-256-gcm"
        password = "secret"
        serverAddress = "ss.example.com"
        serverPort = 8388
        plugin = "v2ray-plugin"
        pluginOpts = "mode=websocket"
    }
}
