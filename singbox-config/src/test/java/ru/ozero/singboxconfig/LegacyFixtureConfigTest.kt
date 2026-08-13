package ru.ozero.singboxconfig

import org.json.JSONObject
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.BeanBlobSchema
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LegacyFixtureConfigTest {
    @Test
    fun `golden legacy fixtures produce complete sing-box configs`() {
        FIXTURES.forEach { (protocolType, name) ->
            val bytes = assertNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }
            val recovered = assertIs<RecoveryResult.Success>(PersistedProfileRecovery.recover(bytes, protocolType))
            val config = JSONObject(recovered.json)

            assertTrue(config.getJSONArray("outbounds").length() > 0, name)
            assertTrue(config.getJSONArray("inbounds").length() > 0, name)
        }
    }

    @Test
    fun `legacy Reality recovers to supported concrete outbound`() {
        val bytes = fixture("vless-reality-vision-70899053.bin")

        val recovered = assertIs<RecoveryResult.Success>(PersistedProfileRecovery.recover(bytes, 0))
        val outbound = JSONObject(recovered.json).getJSONArray("outbounds").getJSONObject(0)
        val tls = outbound.getJSONObject("tls")
        val reality = tls.getJSONObject("reality")
        val utls = tls.getJSONObject("utls")

        assertEquals(BeanBlobSchema.LEGACY_V1, recovered.schema)
        assertEquals(bytes.size, recovered.bytesConsumed)
        assertEquals("vless", outbound.getString("type"))
        assertEquals("198.51.100.10", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("11111111-1111-1111-1111-111111111111", outbound.getString("uuid"))
        assertEquals("xtls-rprx-vision", outbound.getString("flow"))
        assertTrue(!outbound.has("transport") || outbound.getJSONObject("transport").getString("type") == "http")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("reality.example", tls.getString("server_name"))
        assertTrue(reality.getBoolean("enabled"))
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", reality.getString("public_key"))
        assertEquals("a1b2c3d4", reality.getString("short_id"))
        assertTrue(utls.getBoolean("enabled"))
        assertEquals("chrome", utls.getString("fingerprint"))
    }

    @Test
    fun `transitional V2 Reality recovers to supported concrete outbound`() {
        val bytes = transitionalFixture()

        val recovered = assertIs<RecoveryResult.Success>(PersistedProfileRecovery.recover(bytes, 0))
        val outbound = JSONObject(recovered.json).getJSONArray("outbounds").getJSONObject(0)
        val tls = outbound.getJSONObject("tls")
        val reality = tls.getJSONObject("reality")

        assertEquals(BeanBlobSchema.CURRENT_RAW_V2, recovered.schema)
        assertEquals(bytes.size, recovered.bytesConsumed)
        assertEquals("198.51.100.20", outbound.getString("server"))
        assertEquals("44444444-4444-4444-4444-444444444444", outbound.getString("uuid"))
        assertEquals("xtls-rprx-vision", outbound.getString("flow"))
        assertEquals("reality-v2.example", tls.getString("server_name"))
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA", reality.getString("public_key"))
        assertEquals("b1c2d3e4", reality.getString("short_id"))
    }

    @Test
    fun `invalid damaged and protocol mismatched blobs fail recovery`() {
        val bytes = fixture("vless-reality-vision-70899053.bin")

        assertFailure(RecoveryFailureCategory.DECODE_FAILED, PersistedProfileRecovery.recover(byteArrayOf(1), 0))
        assertFailure(
            RecoveryFailureCategory.DECODE_FAILED,
            PersistedProfileRecovery.recover(bytes.copyOf(bytes.size - 1), 0),
        )
        assertFailure(
            RecoveryFailureCategory.DECODE_FAILED,
            PersistedProfileRecovery.recover(bytes + byteArrayOf(1), 0),
        )
        assertFailure(RecoveryFailureCategory.PROTOCOL_MISMATCH, PersistedProfileRecovery.recover(bytes, 1))
    }

    private fun assertFailure(category: RecoveryFailureCategory, result: RecoveryResult) {
        assertEquals(category, assertIs<RecoveryResult.Failure>(result).category)
    }

    private fun fixture(name: String): ByteArray =
        assertNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }

    private fun transitionalFixture(): ByteArray = TRANSITIONAL_FIXTURE_HEX.decodeHex()

    private companion object {
        const val TRANSITIONAL_FIXTURE_HEX =
            "0b0081818181008178746c732d727072782d766973696fee0081006e6f6ee5810081008100816e6f6ee5818181810010" +
                "6e6f6ee5816e6f6ee581818181816e6f6ee57261f7006368726f6de5ac42424242424242424242424242424242424242" +
                "42424242424242424242424242424242424242424242424162316332643365b47265616c6974f93139382e35312e3130" +
                "302e32b0f60600000000000068326d75f87265616c6974792d76322e6578616d706ce581617574ef7463f081a5343434" +
                "34343434342d343434342d343434342d343434342d34343434343434343434343400"
        val FIXTURES = listOf(
            0 to "vless-reality-vision-70899053.bin",
            0 to "vless-ws-tls-70899053.bin",
            1 to "vmess-ws-tls-70899053.bin",
            2 to "trojan-tls-70899053.bin",
            3 to "shadowsocks-70899053.bin",
        )
    }
}

private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
