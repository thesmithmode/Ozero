package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VLESSBeanKryoRoundtripTest {

    @Test
    fun `should round-trip basic VLESS bean through Kryo`() {
        val bean = VLESSBean().apply {
            uuid = "12345678-1234-1234-1234-123456789abc"
            serverAddress = "example.com"
            serverPort = 443
            name = "test-server"
            flow = "xtls-rprx-vision"
            security = "reality"
            sni = "example.com"
            realityPublicKey = "somePublicKey"
            realityShortId = "ab12cd34"
            realityFingerprint = "chrome"
            encryption = "none"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.uuid, restored.uuid)
        assertEquals(bean.serverAddress, restored.serverAddress)
        assertEquals(bean.serverPort, restored.serverPort)
        assertEquals(bean.name, restored.name)
        assertEquals(bean.flow, restored.flow)
        assertEquals(bean.security, restored.security)
        assertEquals(bean.sni, restored.sni)
        assertEquals(bean.realityPublicKey, restored.realityPublicKey)
        assertEquals(bean.realityShortId, restored.realityShortId)
        assertEquals(bean.realityFingerprint, restored.realityFingerprint)
        assertEquals(bean.encryption, restored.encryption)
    }

    @Test
    fun `should round-trip VLESS WebSocket bean`() {
        val bean = VLESSBean().apply {
            uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            serverAddress = "ws.example.com"
            serverPort = 80
            name = "ws-server"
            type = "ws"
            host = "ws.example.com"
            path = "/ws"
            security = "tls"
            sni = "ws.example.com"
            alpn = "h2,http/1.1"
            utlsFingerprint = "firefox"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.type, restored.type)
        assertEquals(bean.host, restored.host)
        assertEquals(bean.path, restored.path)
        assertEquals(bean.alpn, restored.alpn)
        assertEquals(bean.utlsFingerprint, restored.utlsFingerprint)
    }

    @Test
    fun `should produce non-empty byte array`() {
        val bean = VLESSBean().apply {
            uuid = "test-uuid"
            serverAddress = "test.com"
            serverPort = 443
        }
        val bytes = KryoSerializer.serialize(bean)
        assertTrue(bytes.isNotEmpty(), "Serialized bytes should not be empty")
    }

    @Test
    fun `should preserve default values after round-trip`() {
        val bean = VLESSBean()
        bean.initializeDefaultValues()

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals("none", restored.encryption, "VLESSBean encryption default must be 'none'")
        assertEquals("tcp", restored.type, "StandardV2RayBean type default must be 'tcp'")
        assertEquals("none", restored.security, "security default must be 'none'")
    }

    @Test
    fun `should handle fields with special characters`() {
        val bean = VLESSBean().apply {
            uuid = "12345678-0000-0000-0000-000000000001"
            serverAddress = "服务器.example.com"
            serverPort = 8443
            name = "Сервер 'test' & more"
            path = "/path?q=1&r=2"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.serverAddress, restored.serverAddress)
        assertEquals(bean.name, restored.name)
        assertEquals(bean.path, restored.path)
    }

    @Test
    fun `should support multiple round-trips from same pool`() {
        repeat(5) { i ->
            val bean = VLESSBean().apply {
                uuid = "00000000-0000-0000-0000-${i.toString().padStart(12, '0')}"
                serverAddress = "server$i.example.com"
                serverPort = 443 + i
            }
            val bytes = KryoSerializer.serialize(bean)
            val restored: VLESSBean = KryoSerializer.deserialize(bytes)
            assertEquals(bean.serverAddress, restored.serverAddress)
            assertEquals(bean.serverPort, restored.serverPort)
        }
    }

    @Test
    fun `should deserialize only VLESSBean not abstract type`() {
        val bean = VLESSBean().apply {
            uuid = "deadbeef-0000-0000-0000-000000000000"
            serverAddress = "host.example.com"
            serverPort = 1234
        }
        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)
        assertNotNull(restored)
        assertTrue(restored is VLESSBean)
    }

    @Test
    fun `should round-trip gRPC bean with serviceName`() {
        val bean = VLESSBean().apply {
            uuid = "grpc0000-0000-0000-0000-000000000000"
            serverAddress = "grpc.example.com"
            serverPort = 443
            type = "grpc"
            grpcServiceName = "myGrpcService"
            security = "tls"
            sni = "grpc.example.com"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.type, restored.type)
        assertEquals(bean.grpcServiceName, restored.grpcServiceName)
        assertEquals(bean.sni, restored.sni)
    }

    @Test
    fun `should round-trip splithttp transport bean`() {
        val bean = VLESSBean().apply {
            uuid = "splith00-0000-0000-0000-000000000000"
            serverAddress = "sh.example.com"
            serverPort = 443
            type = "splithttp"
            path = "/splithttp"
            host = "sh.example.com"
            security = "tls"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.type, restored.type)
        assertEquals(bean.path, restored.path)
        assertEquals(bean.host, restored.host)
    }

    @Test
    fun `should round-trip http transport bean`() {
        val bean = VLESSBean().apply {
            uuid = "http0000-0000-0000-0000-000000000001"
            serverAddress = "http.example.com"
            serverPort = 80
            type = "http"
            host = "http.example.com"
            path = "/api"
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(bean.type, restored.type)
        assertEquals(bean.host, restored.host)
        assertEquals(bean.path, restored.path)
    }

    @Test
    fun `should preserve port boundary values after round-trip`() {
        for (port in listOf(1, 80, 443, 8080, 65535)) {
            val bean = VLESSBean().apply {
                uuid = "porttest-0000-0000-0000-000000000001"
                serverAddress = "boundary.example.com"
                serverPort = port
            }
            val bytes = KryoSerializer.serialize(bean)
            val restored: VLESSBean = KryoSerializer.deserialize(bytes)
            assertEquals(port, restored.serverPort, "Port $port must survive round-trip")
        }
    }

    @Test
    fun `should preserve long path and host fields`() {
        val longPath = "/long/" + "a".repeat(200)
        val longHost = "sub." + "domain.".repeat(10) + "example.com"
        val bean = VLESSBean().apply {
            uuid = "longfld0-0000-0000-0000-000000000001"
            serverAddress = "long.example.com"
            serverPort = 443
            type = "ws"
            path = longPath
            host = longHost
        }

        val bytes = KryoSerializer.serialize(bean)
        val restored: VLESSBean = KryoSerializer.deserialize(bytes)

        assertEquals(longPath, restored.path)
        assertEquals(longHost, restored.host)
    }
}
