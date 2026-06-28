package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionParserYamlEdgeCoverageTest {

    @Test
    fun `clash parser covers scalar list and shadowsocks cipher edge branches`() {
        val yaml = """
            proxies:
              - name: Scalar ALPN
                type: vless
                server: scalar.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                security: tls
                alpn: h3
                public-key: top-pk
                short-id: top-sid
              - name: Numeric Cipher
                type: ss
                server: numeric.example.com
                port: 8388
                cipher: 2022
                password: pwd
              - name: Boolean Cipher
                type: ss
                server: boolean.example.com
                port: 8388
                cipher: true
                password: pwd
              - name: Map Cipher Without Method
                type: ss
                server: map.example.com
                port: 8388
                cipher:
                  mode: cfb
                  rounds: 2
                password: pwd
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(4, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("h3", vless.alpn)
        assertEquals("reality", vless.security)
        assertEquals("top-pk", vless.realityPublicKey)
        assertEquals("top-sid", vless.realityShortId)
        assertEquals("2022", (result[1] as ShadowsocksBean).method)
        assertEquals("true", (result[2] as ShadowsocksBean).method)
        assertEquals("mode=cfb,rounds=2", (result[3] as ShadowsocksBean).method)
    }

    @Test
    fun `clash parser covers yaml coercion bool aliases and transport direct fields`() {
        assertTrue(ClashYamlParser.parse("proxies:\n  - plain-item\n  - 7\n").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: []\n---\nscalar").isEmpty())

        val yaml = """
            proxies:
              - name:
                  label: mapped-name
                  tier: 1
                type: vless
                server: mapped-name.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                reality: 1
                tls: 0
                allowInsecure: false
                network: ""
              - name:
                  - list
                  - name
                type: trojan
                server: direct-grpc.example.com
                port: 443
                password: secret
                network: grpc
                serviceName: direct-service
                skip-cert-verify: true
              - name: HTTP Upgrade Direct
                type: vmess
                server: httpupgrade-direct.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                httpupgrade-opts:
                  path: /upgrade
                host: direct-host.example.com
                tls: "1"
              - name: SS Method Fallback
                type: ss
                server: ss-method.example.com
                port: 8388
                method: chacha20-ietf-poly1305
                password: secret
                plugin_opts:
                  mode: websocket
                  host: plugin.example.com
              - name: VMess Net Alias
                type: vmess
                server: net-alias.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                net: xhttp
                security: ""
                skip-cert-verify: yes
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(5, result.size)
        val mappedName = result[0] as VLESSBean
        assertEquals("label=mapped-name,tier=1", mappedName.name)
        assertEquals("tcp", mappedName.type)
        assertEquals("reality", mappedName.security)
        assertFalse(mappedName.allowInsecure)
        val directGrpc = result[1] as TrojanBean
        assertEquals("list,name", directGrpc.name)
        assertEquals("grpc", directGrpc.type)
        assertEquals("direct-service", directGrpc.grpcServiceName)
        assertFalse(directGrpc.allowInsecure)
        val httpUpgrade = result[2] as VMessBean
        assertEquals("httpupgrade", httpUpgrade.type)
        assertEquals("/upgrade", httpUpgrade.path)
        assertEquals("direct-host.example.com", httpUpgrade.host)
        assertEquals("tls", httpUpgrade.security)
        val ss = result[3] as ShadowsocksBean
        assertEquals("chacha20-ietf-poly1305", ss.method)
        assertEquals("mode=websocket;host=plugin.example.com", ss.pluginOpts)
        val vmessNetAlias = result[4] as VMessBean
        assertEquals("splithttp", vmessNetAlias.type)
        assertEquals("none", vmessNetAlias.security)
        assertTrue(vmessNetAlias.allowInsecure)
    }

    @Test
    fun `clash shadowsocks plugin opts map uses sip003 semicolon separators while hosts keep commas`() {
        val yaml = """
            proxies:
              - name: H2 Host List
                type: vmess
                server: h2-hosts.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                h2-opts:
                  host:
                    - one.example.com
                    - two.example.com
              - name: SS Plugin Opts Map
                type: ss
                server: ss-plugin.example.com
                port: 8388
                cipher: chacha20-ietf-poly1305
                password: secret
                plugin-opts:
                  obfs: http
                  obfs-host: www.example.com
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(2, result.size)
        assertEquals("one.example.com,two.example.com", (result[0] as VMessBean).host)
        assertEquals("obfs=http;obfs-host=www.example.com", (result[1] as ShadowsocksBean).pluginOpts)
    }
}
