package ru.ozero.singboxconfig

import org.json.JSONObject
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.KryoSerializer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBuilderJsonObjectTest {
    @Test
    fun `supported VLESS Reality grpc survives serialization and generates structured JSON`() {
        val original = VLESSBean().apply {
            serverAddress = "edge.example"
            serverPort = 443
            uuid = "00000000-0000-0000-0000-000000000001"
            type = "grpc"
            security = "reality"
            sni = "front.example"
            host = "authority.example"
            grpcServiceName = "service"
            realityPublicKey = VALID_REALITY_PUBLIC_KEY
            realityShortId = "ab12"
            realityFingerprint = "chrome"
            flow = "xtls-rprx-vision"
            packetEncoding = "xudp"
            allowInsecure = false
            alpn = "h2"
        }

        val restored = KryoSerializer.deserialize<VLESSBean>(KryoSerializer.serialize(original))
        assertEquals(original.serverAddress, restored.serverAddress)
        assertEquals(original.serverPort, restored.serverPort)
        assertEquals(original.uuid, restored.uuid)
        assertEquals(original.type, restored.type)
        assertEquals(original.security, restored.security)
        assertEquals(original.sni, restored.sni)
        assertEquals(original.host, restored.host)
        assertEquals(original.grpcServiceName, restored.grpcServiceName)
        assertEquals(original.realityPublicKey, restored.realityPublicKey)
        assertEquals(original.realityShortId, restored.realityShortId)
        assertEquals(original.realityFingerprint, restored.realityFingerprint)
        assertEquals(original.flow, restored.flow)
        assertEquals(original.packetEncoding, restored.packetEncoding)
        assertEquals(original.allowInsecure, restored.allowInsecure)
        assertEquals(original.alpn, restored.alpn)
        val canonical = ConfigBuilder.canonicalBean(restored)
        assertEquals(BeanSupportDecision.Supported, ConfigBuilder.supportDecisionCanonical(canonical))
        val outbound = JSONObject(ConfigBuilder.buildSingboxConfigFromCanonical(canonical))
            .getJSONArray("outbounds")
            .getJSONObject(0)

        assertEquals("vless", outbound.getString("type"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("xtls-rprx-vision", outbound.getString("flow"))
        assertEquals("xudp", outbound.getString("packet_encoding"))
        val transport = outbound.getJSONObject("transport")
        assertEquals("grpc", transport.getString("type"))
        assertEquals("service", transport.getString("service_name"))
        val tls = outbound.getJSONObject("tls")
        assertEquals("front.example", tls.getString("server_name"))
        assertEquals("h2", tls.getJSONArray("alpn").getString(0))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertEquals(VALID_REALITY_PUBLIC_KEY, tls.getJSONObject("reality").getString("public_key"))
        assertEquals("ab12", tls.getJSONObject("reality").getString("short_id"))
    }

    @Test
    fun `every stock config shape parses as JSON object with real fields`() {
        val vless = vless()
        val vmess = VMessBean().apply {
            serverAddress = "vmess.example"
            serverPort = 443
            uuid = "00000000-0000-0000-0000-000000000002"
            type = "ws"
        }
        val trojan = TrojanBean().apply {
            serverAddress = "trojan.example"
            serverPort = 443
            password = "password"
            type = "grpc"
        }
        val shadowsocks = ShadowsocksBean().apply {
            serverAddress = "198.51.100.4"
            serverPort = 8388
            method = "chacha20-ietf-poly1305"
            password = "password"
        }
        val configs = listOf(
            ConfigBuilder.buildSingboxConfig(vless),
            ConfigBuilder.buildSingboxAutoConfig(listOf(vless, vmess)),
            ConfigBuilder.buildChainConfig(trojan, 2080),
            ConfigBuilder.buildAutoChainConfig(listOf(vless, shadowsocks), 2081),
            ConfigBuilder.buildProfileChainConfig(vless, listOf(shadowsocks)),
            ConfigBuilder.buildSingboxConfig(vmess),
            ConfigBuilder.buildSingboxConfig(trojan),
            ConfigBuilder.buildSingboxConfig(shadowsocks),
        )

        configs.forEach { json ->
            val root = JSONObject(json)
            assertTrue(root.getJSONArray("outbounds").length() > 0)
            assertTrue(root.getJSONObject("dns").getJSONArray("servers").length() > 0)
            assertTrue(root.has("route"))
        }
    }

    @Test
    fun `escaping Unicode controls and DNS remain structured values`() {
        val bean = vless().apply {
            serverAddress = "edge-ñ.example"
            host = "quoted\"host.example"
            path = "/line\n${1.toChar()}"
            type = "ws"
        }

        val root = JSONObject(
            ConfigBuilder.buildSingboxConfig(
                bean,
                dnsServers = listOf("https://dns.example/dns-query"),
                ipv6Enabled = false,
            ),
        )
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val dns = root.getJSONObject("dns")

        assertEquals("edge-ñ.example", outbound.getString("server"))
        assertEquals("/line\n${1.toChar()}", outbound.getJSONObject("transport").getString("path"))
        assertEquals("ipv4_only", dns.getString("strategy"))
        assertFalse(root.toString().contains("http://www.gstatic.com"))
    }

    private fun vless() = VLESSBean().apply {
        serverAddress = "vless.example"
        serverPort = 443
        uuid = "00000000-0000-0000-0000-000000000001"
        type = "tcp"
        security = "none"
    }

    private companion object {
        const val VALID_REALITY_PUBLIC_KEY = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
    }
}
