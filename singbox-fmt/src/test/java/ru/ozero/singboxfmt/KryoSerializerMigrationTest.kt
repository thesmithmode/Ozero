package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.serializers.FieldSerializer
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KryoSerializerMigrationTest {
    @Test
    fun `migrates legacy VLESS Reality Vision blob`() {
        val restored = migrate(
            VLESSBean().apply {
                uuid = "11111111-1111-1111-1111-111111111111"
                serverAddress = "198.51.100.10"
                serverPort = 443
                type = "tcp"
                security = "reality"
                flow = "xtls-rprx-vision"
                realityPublicKey = "public-key"
                realityShortId = "a1b2"
            },
        )

        assertEquals("reality", restored.security)
        assertEquals("xtls-rprx-vision", restored.flow)
        assertEquals("", restored.rawTransportType)
    }

    @Test
    fun `migrates legacy VLESS WebSocket TLS blob`() {
        val restored = migrate(
            VLESSBean().apply {
                uuid = "22222222-2222-2222-2222-222222222222"
                serverAddress = "ws.example"
                type = "ws"
                host = "ws.example"
                path = "/ws"
                security = "tls"
            },
        )

        assertEquals("ws", restored.type)
        assertEquals("ws.example", restored.host)
        assertEquals("/ws", restored.path)
    }

    @Test
    fun `migrates legacy VMess WebSocket TLS blob`() {
        val restored = migrate(
            VMessBean().apply {
                uuid = "33333333-3333-3333-3333-333333333333"
                serverAddress = "vmess.example"
                type = "ws"
                host = "vmess.example"
                path = "/v"
                security = "tls"
            },
        )

        assertEquals("vmess.example", restored.serverAddress)
        assertEquals("ws", restored.type)
    }

    @Test
    fun `migrates legacy Trojan TLS blob`() {
        val restored = migrate(
            TrojanBean().apply {
                password = "password"
                serverAddress = "trojan.example"
                security = "tls"
                sni = "trojan.example"
            },
        )

        assertEquals("password", restored.password)
        assertEquals("tls", restored.security)
    }

    @Test
    fun `migrates legacy Shadowsocks blob`() {
        val restored = migrate(
            ShadowsocksBean().apply {
                serverAddress = "ss.example"
                serverPort = 8388
                method = "aes-128-gcm"
                password = "password"
            },
        )

        assertEquals("aes-128-gcm", restored.method)
        assertEquals(8388, restored.serverPort)
    }

    private inline fun <reified T : AbstractBean> migrate(bean: T): T {
        val decoded = KryoSerializer.deserializeWithMigration(legacyBlob(bean))
        assertNotNull(decoded.migratedBlob)
        assertNull(KryoSerializer.deserializeWithMigration(decoded.migratedBlob).migratedBlob)
        return decoded.bean as T
    }

    private fun legacyBlob(bean: AbstractBean): ByteArray {
        val kryo = Kryo().apply {
            isRegistrationRequired = false
            registerLegacyStandard(VLESSBean::class.java)
            registerLegacyStandard(VMessBean::class.java)
            registerLegacyStandard(TrojanBean::class.java)
            register(ShadowsocksBean::class.java)
            registerLegacyStandard(StandardV2RayBean::class.java)
            register(AbstractBean::class.java)
        }
        return ByteArrayOutputStream().use { bytes ->
            ByteBufferOutput(bytes).use { output ->
                kryo.writeClassAndObject(output, bean)
                output.flush()
            }
            bytes.toByteArray()
        }
    }

    private fun <T : StandardV2RayBean> Kryo.registerLegacyStandard(type: Class<T>) {
        val serializer = FieldSerializer<T>(this, type)
        serializer.removeField("rawTransportType")
        register(type, serializer)
    }
}
