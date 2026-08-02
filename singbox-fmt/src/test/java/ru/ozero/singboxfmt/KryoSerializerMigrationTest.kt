package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferOutput
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KryoSerializerMigrationTest {
    @Test
    fun `migrates golden VLESS Reality Vision blob from 70899053`() {
        val restored = assertIs<VLESSBean>(migrateFixture("vless-reality-vision-70899053.bin"))

        assertEquals("198.51.100.10", restored.serverAddress)
        assertEquals("reality", restored.security)
        assertEquals("xtls-rprx-vision", restored.flow)
        assertEquals("", restored.rawTransportType)
    }

    @Test
    fun `migrates golden VLESS WebSocket TLS blob from 70899053`() {
        val restored = assertIs<VLESSBean>(migrateFixture("vless-ws-tls-70899053.bin"))

        assertEquals("ws", restored.type)
        assertEquals("ws.example", restored.host)
        assertEquals("/ws", restored.path)
    }

    @Test
    fun `migrates golden VMess WebSocket TLS blob from 70899053`() {
        val restored = assertIs<VMessBean>(migrateFixture("vmess-ws-tls-70899053.bin"))

        assertEquals("198.51.100.12", restored.serverAddress)
        assertEquals("ws", restored.type)
    }

    @Test
    fun `migrates golden Trojan TLS blob from 70899053`() {
        val restored = assertIs<TrojanBean>(migrateFixture("trojan-tls-70899053.bin"))

        assertEquals("fixture-password", restored.password)
        assertEquals("tls", restored.security)
    }

    @Test
    fun `migrates golden Shadowsocks blob from 70899053`() {
        val restored = assertIs<ShadowsocksBean>(migrateFixture("shadowsocks-70899053.bin"))

        assertEquals("aes-128-gcm", restored.method)
        assertEquals(8388, restored.serverPort)
    }

    @Test
    fun `current raw V2 is decoded before legacy V1 and preserves raw transport`() {
        val current = VLESSBean().apply {
            serverAddress = "198.51.100.20"
            serverPort = 443
            uuid = "44444444-4444-4444-4444-444444444444"
            type = "tcp"
            rawTransportType = "raw"
            security = "tls"
        }

        val restored = assertIs<VLESSBean>(KryoSerializer.deserializeWithMigration(currentRawBlob(current)).bean)

        assertEquals("raw", restored.rawTransportType)
    }

    @Test
    fun `trailing payload is rejected before migration rewrite`() {
        val fixture = fixture("vless-ws-tls-70899053.bin")

        assertFails { KryoSerializer.deserializeWithMigration(fixture + byteArrayOf(1)) }
    }

    private fun migrateFixture(name: String): AbstractBean {
        val decoded = KryoSerializer.deserializeWithMigration(fixture(name))
        val migrated = assertNotNull(decoded.migratedBlob)
        assertNull(KryoSerializer.deserializeWithMigration(migrated).migratedBlob)
        return decoded.bean
    }

    private fun fixture(name: String): ByteArray =
        assertNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }

    private fun currentRawBlob(bean: AbstractBean): ByteArray {
        val kryo = Kryo().apply {
            isRegistrationRequired = false
            register(VLESSBean::class.java)
            register(VMessBean::class.java)
            register(TrojanBean::class.java)
            register(ShadowsocksBean::class.java)
            register(StandardV2RayBean::class.java)
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
}
