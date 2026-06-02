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

@Suppress("LongMethod")
class ConfigBuilderBranchCoverageTest {

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
            host = "front.example.com,other.example.com"
            allowInsecure = true
            certificates = "-----BEGIN CERTIFICATE-----"
        }

        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"server_name\":\"server.example.com\"")
        assertContains(json, "\"insecure\":true")
        assertContains(json, "\"certificate\":\"-----BEGIN CERTIFICATE-----\"")
    }

    @Test
    fun `httpupgrade transport omits empty fields and emits host when present`() {
        val empty = ConfigBuilder.buildSingboxConfig(vless().apply { type = "httpupgrade" })
        val filled = ConfigBuilder.buildSingboxConfig(vless().apply {
            type = "httpupgrade"
            host = "front.example.com"
            path = "/upgrade"
        })

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
    fun `wireguard outbound omits optional keepalive and detour when absent`() {
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
        assertFalse(json.contains("\"detour\""))
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
