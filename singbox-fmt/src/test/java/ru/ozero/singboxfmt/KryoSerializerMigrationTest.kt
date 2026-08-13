package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferOutput
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KryoSerializerMigrationTest {
    @Test
    fun `migrates golden VLESS Reality Vision blob from 70899053`() {
        val restored = assertIs<VLESSBean>(migrateFixture("vless-reality-vision-70899053.bin"))

        assertEquals("198.51.100.10", restored.serverAddress)
        assertEquals(443, restored.serverPort)
        assertEquals("11111111-1111-1111-1111-111111111111", restored.uuid)
        assertEquals("tcp", restored.type)
        assertEquals("reality", restored.security)
        assertEquals("xtls-rprx-vision", restored.flow)
        assertEquals("", restored.rawTransportType)
        assertEquals("reality.example", restored.sni)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", restored.realityPublicKey)
        assertEquals("a1b2c3d4", restored.realityShortId)
        assertEquals("chrome", restored.realityFingerprint)
        assertEquals("none", restored.packetEncoding)
        assertEquals(false, restored.allowInsecure)
    }

    @Test
    fun `legacy Reality fixture has one fully consumed V1 candidate`() {
        val bytes = fixture("vless-reality-vision-70899053.bin")

        val candidate = KryoSerializer.decodeCandidates(bytes).single()

        assertEquals(BeanBlobSchema.LEGACY_V1, candidate.schema)
        assertEquals(bytes.size, candidate.bytesConsumed)
        assertEquals(bytes.size, candidate.blobSize)
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
    fun `low-level decoder reports every fully consumed Shadowsocks schema`() {
        val bytes = fixture("shadowsocks-70899053.bin")

        val candidates = KryoSerializer.decodeCandidates(bytes)

        assertEquals(
            setOf(BeanBlobSchema.CURRENT_RAW_V2, BeanBlobSchema.LEGACY_V1),
            candidates.map { it.schema }.toSet(),
        )
        candidates.forEach { candidate ->
            val restored = assertIs<ShadowsocksBean>(candidate.bean)
            assertEquals(bytes.size, candidate.bytesConsumed)
            assertEquals("aes-128-gcm", restored.method)
            assertEquals(8388, restored.serverPort)
        }
    }

    @Test
    fun `current raw V2 produces one fully consumed candidate and preserves raw transport`() {
        val current = VLESSBean().apply {
            serverAddress = "198.51.100.20"
            serverPort = 443
            uuid = "44444444-4444-4444-4444-444444444444"
            type = "tcp"
            rawTransportType = "raw"
            security = "tls"
        }

        val blob = currentRawBlob(current)
        val candidate = KryoSerializer.decodeCandidates(blob).single()
        val restored = assertIs<VLESSBean>(candidate.bean)

        assertEquals(BeanBlobSchema.CURRENT_RAW_V2, candidate.schema)
        assertEquals(blob.size, candidate.bytesConsumed)
        assertEquals("raw", restored.rawTransportType)
    }

    @Test
    fun `transitional 0672c82 Reality fixture is only a fully consumed current V2 candidate`() {
        val bytes = transitionalFixture()

        val candidate = KryoSerializer.decodeCandidates(bytes).single()
        val restored = assertIs<VLESSBean>(candidate.bean)

        assertEquals(BeanBlobSchema.CURRENT_RAW_V2, candidate.schema)
        assertEquals(bytes.size, candidate.bytesConsumed)
        assertEquals(bytes.size, candidate.blobSize)
        assertEquals("198.51.100.20", restored.serverAddress)
        assertEquals(443, restored.serverPort)
        assertEquals("44444444-4444-4444-4444-444444444444", restored.uuid)
        assertEquals("tcp", restored.type)
        assertEquals("raw", restored.rawTransportType)
        assertEquals("reality", restored.security)
        assertEquals("xtls-rprx-vision", restored.flow)
        assertEquals("reality-v2.example", restored.sni)
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA", restored.realityPublicKey)
        assertEquals("b1c2d3e4", restored.realityShortId)
        assertEquals("chrome", restored.realityFingerprint)
        assertEquals("none", restored.packetEncoding)
        assertEquals(false, restored.allowInsecure)
        assertTrue(KryoSerializer.decodeCandidates(bytes.copyOf(bytes.size - 1)).isEmpty())
        assertTrue(KryoSerializer.decodeCandidates(bytes + byteArrayOf(1)).isEmpty())
    }

    @Test
    fun `transitional fixture matches recorded historical checksum`() {
        val expected = TRANSITIONAL_FIXTURE_SHA256
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(transitionalFixture())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, actual)
    }

    @Test
    fun `versioned blob produces one versioned candidate`() {
        val blob = KryoSerializer.serialize(validVless())

        val candidate = KryoSerializer.decodeCandidates(blob).single()

        assertEquals(BeanBlobSchema.VERSIONED_V1, candidate.schema)
        assertEquals(blob.size, candidate.bytesConsumed)
    }

    @Test
    fun `corrupt truncated and trailing blobs produce no candidates`() {
        val fixture = fixture("vless-reality-vision-70899053.bin")

        assertTrue(KryoSerializer.decodeCandidates(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(KryoSerializer.decodeCandidates(fixture.copyOf(fixture.size - 1)).isEmpty())
        assertTrue(KryoSerializer.decodeCandidates(fixture + byteArrayOf(1)).isEmpty())
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

    private fun transitionalFixture(): ByteArray = TRANSITIONAL_FIXTURE_HEX.decodeHex()

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

    private fun validVless() = VLESSBean().apply {
        serverAddress = "198.51.100.20"
        serverPort = 443
        uuid = "44444444-4444-4444-4444-444444444444"
        security = "tls"
    }

    private companion object {
        const val TRANSITIONAL_FIXTURE_HEX =
            "0b0081818181008178746c732d727072782d766973696fee0081006e6f6ee5810081008100816e6f6ee5818181810010" +
                "6e6f6ee5816e6f6ee581818181816e6f6ee57261f7006368726f6de5ac42424242424242424242424242424242424242" +
                "42424242424242424242424242424242424242424242424162316332643365b47265616c6974f93139382e35312e3130" +
                "302e32b0f60600000000000068326d75f87265616c6974792d76322e6578616d706ce581617574ef7463f081a5343434" +
                "34343434342d343434342d343434342d343434342d34343434343434343434343400"
        const val TRANSITIONAL_FIXTURE_SHA256 = "8ce0979851df3888dc4aed491dbf892c6223d854c44291e5789a02125126cdb6"
    }
}

private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
