package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawShareLinksParserEdgeTest {

    @Test
    fun `should parse sing-box outbound validation edge cases`() {
        val json = """
            {
              "outbounds": [
                { "type": "trojan", "server": "", "server_port": 443, "password": "x" },
                { "type": "vmess", "server": "no-port.example.com", "uuid": "id" },
                { "type": "vless", "server": "bad-port.example.com", "server_port": 0, "uuid": "id" },
                {
                  "type": "vmess",
                  "tag": "Plain VMess",
                  "server": "plain-vmess.example.com",
                  "server_port": 80,
                  "uuid": "12345678-1234-1234-1234-123456789abc"
                },
                {
                  "type": "trojan",
                  "tag": "TLS No UTLS",
                  "server": "tls-trojan.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "tls": { "enabled": true, "server_name": "front.example.com" }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val vmess = result[0] as VMessBean
        assertEquals("tcp", vmess.type)
        assertEquals("none", vmess.security)
        val trojan = result[1] as TrojanBean
        assertEquals("tls", trojan.security)
        assertEquals("", trojan.utlsFingerprint)
    }

    @Test
    fun `should parse clash explicit security missing tls and scalar helpers`() {
        val yaml = """
            proxies:
              - name: Explicit Security
                type: vless
                server: explicit.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                security: xtls
                path: /direct
                host: direct.example.com
                service-name: direct-svc
              - name: TLS Disabled
                type: vmess
                server: disabled.example.com
                port: 80
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: false
              - name: Map String
                type: ss
                server: ss-map.example.com
                port: 8388
                cipher:
                  method: aes-128-gcm
                password: pwd
              - name: String Bool False
                type: trojan
                server: false.example.com
                port: 443
                password: pwd
                skip-cert-verify: no
                allowInsecure: 0
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(4, result.size)
        val explicit = result[0] as VLESSBean
        assertEquals("xtls", explicit.security)
        assertEquals("/direct", explicit.path)
        assertEquals("direct.example.com", explicit.host)
        assertEquals("direct-svc", explicit.grpcServiceName)
        val disabled = result[1] as VMessBean
        assertEquals("none", disabled.security)
        val ss = result[2] as ShadowsocksBean
        assertEquals("method=aes-128-gcm", ss.method)
        val trojan = result[3] as TrojanBean
        assertEquals("tls", trojan.security)
        assertFalse(trojan.allowInsecure)
    }

    @Test
    fun `should skip clash non list proxies and unknown proxy type`() {
        assertTrue(RawShareLinksParser.parse("proxies:\n  name: not-list").isEmpty())
        assertTrue(
            RawShareLinksParser.parse(
                """
                proxies:
                  - name: Unknown
                    type: direct
                    server: direct.example.com
                    port: 1
                """.trimIndent(),
            ).isEmpty(),
        )
    }
}
