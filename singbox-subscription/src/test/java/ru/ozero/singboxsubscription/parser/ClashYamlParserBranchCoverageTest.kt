package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClashYamlParserBranchCoverageTest {

    @Test
    fun `parse returns empty for missing proxies malformed yaml and non list proxies`() {
        assertTrue(ClashYamlParser.parse("proxy-groups:\n  - name: direct").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: [").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  name: not-a-list").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  - type: vless\n    server: ''\n    port: 443").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  - type: vmess\n    server: example.com\n    port: 0").isEmpty())
    }

    @Test
    fun `parse covers nested transport fallbacks and h2 normalization`() {
        val yaml = """
            proxies:
              - name: websocket
                type: vless
                server: ws.example.com
                port: 443
                uuid: uuid
                ws-opts:
                  path: /ws
                  headers:
                    Host: front.example.com
              - name: http-list-host
                type: vmess
                server: h2.example.com
                port: "443"
                uuid: uuid
                h2-opts:
                  path: /h2
                  host:
                    - a.example.com
                    - b.example.com
              - name: grpc-nested
                type: trojan
                server: grpc.example.com
                port: 443
                password: secret
                grpc-opts:
                  grpc-service-name: svc
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(3, parsed.size)
        val ws = parsed[0] as VLESSBean
        assertEquals("ws", ws.type)
        assertEquals("/ws", ws.path)
        assertEquals("front.example.com", ws.host)
        val h2 = parsed[1] as VMessBean
        assertEquals("http", h2.type)
        assertEquals("/h2", h2.path)
        assertEquals("a.example.com,b.example.com", h2.host)
        val grpc = parsed[2] as TrojanBean
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.grpcServiceName)
    }

    @Test
    fun `parse covers aliases defaults unknown types and shadowsocks map method`() {
        val yaml = """
            proxies:
              - name: unsupported
                type: tuic
                server: tuic.example.com
                port: 443
              - name: vless-aliases
                type: vless
                server: alias.example.com
                port: 443
                uuid: uuid
                net: h2
                security: tls
                servername: sni.example.com
                client-fingerprint: chrome
              - name: shadowsocks-map-method
                type: shadowsocks
                server: ss.example.com
                port: 8388
                cipher:
                  method: aes-128-gcm
                  ignored: value
                password: secret
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(2, parsed.size)
        val vless = parsed[0] as VLESSBean
        assertEquals("http", vless.type)
        assertEquals("tls", vless.security)
        assertEquals("sni.example.com", vless.sni)
        assertEquals("chrome", vless.realityFingerprint)
        val ss = parsed[1] as ShadowsocksBean
        assertEquals("aes-128-gcm", ss.method)
        assertEquals("secret", ss.password)
    }
}
