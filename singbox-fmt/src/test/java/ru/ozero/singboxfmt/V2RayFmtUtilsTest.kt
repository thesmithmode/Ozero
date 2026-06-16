package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V2RayFmtUtilsTest {

    @Test
    fun `mapTransportType normalizes sing-box aliases`() {
        assertEquals("http", V2RayFmtUtils.mapTransportType("h2"))
        assertEquals("splithttp", V2RayFmtUtils.mapTransportType("xhttp"))
        assertEquals("grpc", V2RayFmtUtils.mapTransportType("grpc"))
    }

    @Test
    fun `tryBase64Decode supports url and mime encodings without padding`() {
        assertEquals("hello", V2RayFmtUtils.tryBase64Decode("aGVsbG8"))
        assertEquals("hello", V2RayFmtUtils.tryBase64Decode("aGVs\r\nbG8="))
        assertNull(V2RayFmtUtils.tryBase64Decode(""))
        assertNull(V2RayFmtUtils.tryBase64Decode("%%%"))
    }

    @Test
    fun `parseSecurityParams applies defaults and truthy insecure aliases`() {
        val bean = VLESSBean()
        val parsed = UriCompat.parse(
            "vless://id@example.com:443?security=reality&sni=front.example.com&alpn=h2,http/1.1" +
                "&fp=chrome&pbk=public&sid=short&skip-cert-verify=yes",
        )

        V2RayFmtUtils.parseSecurityParams(bean, parsed)

        assertEquals("reality", bean.security)
        assertEquals("front.example.com", bean.sni)
        assertEquals("h2,http/1.1", bean.alpn)
        assertEquals("chrome", bean.utlsFingerprint)
        assertEquals("public", bean.realityPublicKey)
        assertEquals("short", bean.realityShortId)
        assertEquals("chrome", bean.realityFingerprint)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `parseSecurityParams uses safe defaults`() {
        val bean = VLESSBean()

        V2RayFmtUtils.parseSecurityParams(bean, UriCompat.parse("vless://id@example.com:443"))

        assertEquals("none", bean.security)
        assertEquals("", bean.sni)
        assertEquals("", bean.alpn)
        assertEquals("", bean.utlsFingerprint)
        assertEquals("", bean.realityPublicKey)
        assertEquals("", bean.realityShortId)
        assertEquals("chrome", bean.realityFingerprint)
        assertFalse(bean.allowInsecure)
    }

    @Test
    fun `parseTransportParams handles ws http grpc and tcp http header`() {
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(
                bean,
                UriCompat.parse("vless://id@example.com:443?type=ws&host=front.example.com&path=/ws"),
            )
            assertEquals("ws", bean.type)
            assertEquals("front.example.com", bean.host)
            assertEquals("/ws", bean.path)
        }
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(
                bean,
                UriCompat.parse("vless://id@example.com:443?type=h2&host=h2.example.com&path=/h2"),
            )
            assertEquals("http", bean.type)
            assertEquals("h2.example.com", bean.host)
            assertEquals("/h2", bean.path)
        }
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(
                bean,
                UriCompat.parse("vless://id@example.com:443?type=grpc&serviceName=svc"),
            )
            assertEquals("grpc", bean.type)
            assertEquals("svc", bean.grpcServiceName)
        }
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(
                bean,
                UriCompat.parse("vless://id@example.com:443?type=tcp&headerType=http&host=tcp.example.com&path=/tcp"),
            )
            assertEquals("tcp", bean.type)
            assertEquals("http", bean.headerType)
            assertEquals("tcp.example.com", bean.host)
            assertEquals("/tcp", bean.path)
        }
    }

    @Test
    fun `parseTransportParams covers default branches without optional values`() {
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(bean, UriCompat.parse("vless://id@example.com:443?type=tcp"))
            assertEquals("tcp", bean.type)
            assertEquals("none", bean.headerType)
            assertEquals("", bean.host)
            assertEquals("", bean.path)
        }
        VLESSBean().also { bean ->
            V2RayFmtUtils.parseTransportParams(bean, UriCompat.parse("vless://id@example.com:443?type=httpupgrade"))
            assertEquals("httpupgrade", bean.type)
            assertEquals("", bean.host)
            assertEquals("/", bean.path)
        }
    }
}
