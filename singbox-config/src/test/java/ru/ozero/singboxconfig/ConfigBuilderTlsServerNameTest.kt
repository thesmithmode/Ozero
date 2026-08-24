package ru.ozero.singboxconfig

import java.util.Base64
import org.json.JSONObject
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.V2RayFmt
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConfigBuilderTlsServerNameTest {
    private val validRealityPublicKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"

    @Test
    fun `blank sni uses transport host for every imported tls protocol and endpoint kind`() {
        listOf("gateway.example.com", "203.0.113.10").forEach { endpoint ->
            listOf(vless(endpoint), vmess(endpoint), trojan(endpoint)).forEach { bean ->
                assertEquals("transport.example.com", serverName(bean))
            }
        }
    }

    @Test
    fun `blank sni without transport host uses domain endpoint for every tls protocol`() {
        listOf(vless(), vmess(), trojan()).forEach { bean ->
            bean.host = ""

            assertEquals("gateway.example.com", serverName(bean))
        }
    }

    @Test
    fun `blank sni without transport host omits server name for ip endpoints`() {
        listOf(vless("203.0.113.10"), vmess("203.0.113.10"), trojan("203.0.113.10")).forEach { bean ->
            bean.host = ""

            assertFalse(tls(bean).has("server_name"))
        }
    }

    @Test
    fun `explicit sni remains authoritative over transport host and endpoint`() {
        listOf(vless(), vmess(), trojan()).forEach { bean ->
            bean.sni = "certificate.example.com"

            assertEquals("certificate.example.com", serverName(bean))
        }
    }

    @Test
    fun `reality with blank sni uses transport host for domain and ip endpoints`() {
        listOf("gateway.example.com", "203.0.113.10").forEach { endpoint ->
            val bean = vless(endpoint).apply {
                security = "reality"
                realityPublicKey = validRealityPublicKey
                realityShortId = "ab12"
            }

            val tls = tls(bean)

            assertEquals("transport.example.com", tls.getString("server_name"))
            assertEquals(true, tls.getJSONObject("reality").getBoolean("enabled"))
        }
    }

    private fun vless(endpoint: String = "gateway.example.com") = V2RayFmt.parseVLESS(
        "vless://12345678-1234-1234-1234-123456789abc@$endpoint:443" +
            "?type=ws&security=tls&host=transport.example.com&path=%2Ftransport",
    )

    private fun vmess(endpoint: String = "gateway.example.com") = V2RayFmt.parseVMess(
        "vmess://" + Base64.getEncoder().encodeToString(
            vmessJson(endpoint)
                .toByteArray(),
        ),
    )

    private fun vmessJson(endpoint: String): String = JSONObject()
        .put("v", "2")
        .put("ps", "TLS")
        .put("add", endpoint)
        .put("port", "443")
        .put("id", "12345678-1234-1234-1234-123456789abc")
        .put("aid", "0")
        .put("net", "ws")
        .put("type", "none")
        .put("host", "transport.example.com")
        .put("path", "/transport")
        .put("tls", "tls")
        .toString()

    private fun trojan(endpoint: String = "gateway.example.com") = V2RayFmt.parseTrojan(
        "trojan://secret@$endpoint:443?type=ws&security=tls&host=transport.example.com&path=%2Ftransport",
    )

    private fun serverName(bean: StandardV2RayBean): String = tls(bean).getString("server_name")

    private fun tls(bean: StandardV2RayBean): JSONObject {
        val config = JSONObject(ConfigBuilder.buildSingboxConfig(bean))
        return config.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")
    }
}
