package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.V2RayFmt
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBuilderVLESSTest {

    private fun makeBean(
        uuid: String = "12345678-1234-1234-1234-123456789abc",
        host: String = "proxy.example.com",
        port: Int = 443,
        flow: String = "",
        type: String = "tcp",
        security: String = "none",
    ) = VLESSBean().apply {
        this.uuid = uuid
        this.serverAddress = host
        this.serverPort = port
        this.flow = flow
        this.type = type
        this.security = security
    }

    @Test
    fun `should produce valid JSON structure for basic VLESS bean`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertTrue(json.startsWith("{"), "Output must start with {")
        assertTrue(json.endsWith("}"), "Output must end with }")
        assertContains(json, "\"type\":\"vless\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"outbounds\":")
        assertContains(json, "\"route\":")
        assertContains(json, "\"log\":")
    }

    @Test
    fun `should embed server address and port`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(host = "my.server.com", port = 8443))

        assertContains(json, "\"my.server.com\"")
        assertContains(json, "8443")
    }

    @Test
    fun `should embed UUID`() {
        val uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val json = ConfigBuilder.buildSingboxConfig(makeBean(uuid = uuid))

        assertContains(json, uuid)
    }

    @Test
    fun `should include flow when non-empty`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(flow = "xtls-rprx-vision"))

        assertContains(json, "\"flow\"")
        assertContains(json, "xtls-rprx-vision")
    }

    @Test
    fun `should normalize vision flow suffix rejected by sing-box`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(flow = "xtls-rprx-vision-udp443"))

        assertContains(json, "\"flow\":\"xtls-rprx-vision\"")
        assertFalse(
            json.contains("xtls-rprx-vision-udp443"),
            "sing-box rejects xtls-rprx-vision-* suffixes as unsupported flow",
        )
    }

    @Test
    fun `should omit unknown VLESS flow instead of generating invalid config`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(flow = "unsupported-flow"))

        assertFalse(json.contains("\"flow\""), "unsupported flow must not brick the whole sing-box config")
    }

    @Test
    fun `should omit flow field when empty`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(flow = ""))

        assertFalse(json.contains("\"flow\""), "flow field must not appear when empty")
    }

    @Test
    fun `should include direct and block outbounds`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertContains(json, "\"type\":\"direct\"")
        assertContains(json, "\"type\":\"block\"")
        assertContains(json, "\"tag\":\"direct\"")
        assertContains(json, "\"tag\":\"block\"")
    }

    @Test
    fun `should include route with auto_detect_interface`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertContains(json, "\"auto_detect_interface\":true")
        assertContains(json, "\"final\":\"proxy\"")
    }

    @Test
    fun `tun inbound must use address array and auto_route false for Android VPN`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertContains(json, "\"address\":[\"172.19.0.1/30\"")
        assertContains(json, "\"auto_route\":false")
        assertFalse(
            json.contains("\"inet4_address\""),
            "inet4_address deprecated in sing-box 1.10+ — use address array",
        )
        assertFalse(
            json.contains("\"inet6_address\""),
            "inet6_address deprecated in sing-box 1.10+ — use address array",
        )
    }

    @Test
    fun `dns servers must use type and server fields for sing-box 1_13`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertContains(json, "\"type\":\"udp\"")
        assertContains(json, "\"server\":\"1.1.1.1\"")
    }

    @Test
    fun `should include xudp packet encoding`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        assertContains(json, "\"packet_encoding\":\"xudp\"")
    }

    @Test
    fun `should not include tls block when security is none`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean(security = "none"))

        assertFalse(json.contains("\"tls\""), "No tls block for security=none")
    }

    @Test
    fun `should include tls block when security is tls`() {
        val bean = makeBean(security = "tls").apply {
            sni = "proxy.example.com"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"tls\":")
        assertContains(json, "\"enabled\":true")
        assertContains(json, "proxy.example.com")
    }

    @Test
    fun `should include reality block when security is reality`() {
        val bean = makeBean(security = "reality").apply {
            sni = "reality.example.com"
            realityPublicKey = "testPublicKey123"
            realityShortId = "ab12"
            realityFingerprint = "chrome"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"reality\":")
        assertContains(json, "\"enabled\":true")
        assertContains(json, "testPublicKey123")
        assertContains(json, "ab12")
        assertContains(json, "chrome")
    }

    @Test
    fun `should include WebSocket transport for ws type`() {
        val bean = makeBean(type = "ws").apply {
            host = "ws.example.com"
            path = "/ws"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"transport\":")
        assertContains(json, "\"type\":\"ws\"")
        assertContains(json, "/ws")
    }

    @Test
    fun `should normalize legacy numeric websocket early data header into max early data`() {
        val bean = makeBean(type = "ws").apply {
            earlyDataHeaderName = "2048"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"max_early_data\":2048")
        assertFalse(
            json.contains("\"early_data_header_name\":2048"),
            "numeric early_data_header_name bricks sing-box checkConfig",
        )
    }

    @Test
    fun `should emit numeric websocket early data header name as string when it is explicit`() {
        val bean = makeBean(type = "ws").apply {
            maxEarlyData = 2048
            earlyDataHeaderName = "1"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"max_early_data\":2048")
        assertContains(json, "\"early_data_header_name\":\"1\"")
        assertFalse(
            json.contains("\"early_data_header_name\":1"),
            "sing-box expects early_data_header_name to be a JSON string",
        )
    }

    @Test
    fun `tls server name falls back to websocket host before server address`() {
        val bean = makeBean(host = "203.0.113.10", type = "ws", security = "tls").apply {
            this.host = "front.example.com"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"server_name\":\"front.example.com\"")
        assertFalse(json.contains("\"server_name\":\"203.0.113.10\""))
    }

    @Test
    fun `tls server name alias from VLESS subscription is preserved in generated config`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://12345678-1234-1234-1234-123456789abc@203.0.113.10:443" +
                "?security=tls&serverName=front.example.com#Private",
        )

        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"server_name\":\"front.example.com\"")
        assertFalse(json.contains("\"server_name\":\"203.0.113.10\""))
    }

    @Test
    fun `should include gRPC transport for grpc type`() {
        val bean = makeBean(type = "grpc").apply {
            grpcServiceName = "myservice"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"type\":\"grpc\"")
        assertContains(json, "myservice")
    }

    @Test
    fun `should escape special JSON characters in server address`() {
        val bean = makeBean(host = "server\"with\\special.com")
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "server\\\"with\\\\special.com")
    }

    @Test
    fun `should produce config smaller than 10KB`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())

        val sizeBytes = json.toByteArray(Charsets.UTF_8).size
        assertTrue(
            sizeBytes < 10_240,
            "VLESS config must be < 10KB for inline AIDL binder, got ${sizeBytes}B",
        )
    }

    @Test
    fun `should fail for unsupported bean type`() {
        val unsupportedBean = object : ru.ozero.singboxfmt.AbstractBean() {}

        val result = runCatching { ConfigBuilder.buildSingboxConfig(unsupportedBean) }
        assertTrue(result.isFailure, "buildSingboxConfig must throw for unknown bean types")
    }

    @Test
    fun `should include http transport block for http type`() {
        val bean = makeBean(type = "http").apply {
            host = "http.example.com"
            path = "/api"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"transport\":")
        assertContains(json, "\"type\":\"http\"")
        assertContains(json, "/api")
    }

    @Test
    fun `should reject splithttp transport as unsupported`() {
        val bean = makeBean(type = "splithttp").apply {
            host = "splithttp.example.com"
            path = "/stream"
        }
        assertFalse(ConfigBuilder.isSupportedBean(bean), "splithttp not supported by libbox")
        val result = runCatching { ConfigBuilder.buildSingboxConfig(bean) }
        assertTrue(result.isFailure, "buildSingboxConfig must throw for splithttp")
    }

    @Test
    fun `auto config should filter out unsupported splithttp beans`() {
        val supported = makeBean(type = "tcp")
        val unsupported = makeBean(type = "splithttp").apply {
            host = "splithttp.example.com"
        }
        val json = ConfigBuilder.buildSingboxAutoConfig(listOf(supported, unsupported))
        assertFalse(json.contains("splithttp"), "splithttp beans must be filtered out")
        assertContains(json, "\"type\":\"vless\"")
    }

    @Test
    fun `should include alpn array in tls block when alpn set`() {
        val bean = makeBean(security = "tls").apply {
            sni = "alpn.example.com"
            alpn = "h2,http/1.1"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"alpn\"")
        assertContains(json, "h2")
        assertContains(json, "http/1.1")
    }

    @Test
    fun `should include utls fingerprint in tls block when set`() {
        val bean = makeBean(security = "tls").apply {
            sni = "fp.example.com"
            utlsFingerprint = "firefox"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "firefox")
    }

    @Test
    fun `should include both reality block and flow when combined`() {
        val bean = makeBean(security = "reality", flow = "xtls-rprx-vision").apply {
            sni = "reality.example.com"
            realityPublicKey = "realityKey123"
            realityShortId = "aabb1234"
            realityFingerprint = "safari"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"reality\":")
        assertContains(json, "xtls-rprx-vision")
        assertContains(json, "realityKey123")
    }

    @Test
    fun `should include both transport and tls blocks for ws with tls security`() {
        val bean = makeBean(type = "ws", security = "tls").apply {
            host = "wstls.example.com"
            path = "/wss"
            sni = "wstls.example.com"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"tls\":")
        assertContains(json, "\"transport\":")
        assertContains(json, "\"type\":\"ws\"")
    }

    @Test
    fun `should include both transport and tls blocks for grpc with tls security`() {
        val bean = makeBean(type = "grpc", security = "tls").apply {
            grpcServiceName = "secureService"
            sni = "grpctls.example.com"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        assertContains(json, "\"tls\":")
        assertContains(json, "\"type\":\"grpc\"")
        assertContains(json, "secureService")
    }

    @Test
    fun `should produce config smaller than 10KB with all optional fields populated`() {
        val bean = VLESSBean().apply {
            uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff"
            serverAddress = "full.example.com"
            serverPort = 8443
            flow = "xtls-rprx-vision"
            type = "ws"
            security = "tls"
            sni = "full.example.com"
            alpn = "h2,http/1.1"
            utlsFingerprint = "chrome"
            host = "full.example.com"
            path = "/websocket"
            grpcServiceName = "grpcService"
            realityPublicKey = "testPublicKey"
            realityShortId = "deadbeef"
            realityFingerprint = "safari"
            name = "full-server"
            encryption = "none"
        }
        val json = ConfigBuilder.buildSingboxConfig(bean)

        val sizeBytes = json.toByteArray(Charsets.UTF_8).size
        assertTrue(sizeBytes < 10_240, "Full VLESS config must be < 10KB, got ${sizeBytes}B")
    }
}
