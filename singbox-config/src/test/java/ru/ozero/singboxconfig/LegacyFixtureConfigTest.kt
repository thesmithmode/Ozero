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
    fun `invalid damaged and protocol mismatched blobs fail recovery`() {
        val bytes = fixture("vless-reality-vision-70899053.bin")

        assertEquals(RecoveryResult.MigrationFailed, PersistedProfileRecovery.recover(byteArrayOf(1), 0))
        assertEquals(RecoveryResult.MigrationFailed, PersistedProfileRecovery.recover(bytes.copyOf(bytes.size - 1), 0))
        assertEquals(RecoveryResult.MigrationFailed, PersistedProfileRecovery.recover(bytes + byteArrayOf(1), 0))
        assertEquals(RecoveryResult.MigrationFailed, PersistedProfileRecovery.recover(bytes, 1))
    }

    private fun fixture(name: String): ByteArray =
        assertNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }

    private companion object {
        val FIXTURES = listOf(
            0 to "vless-reality-vision-70899053.bin",
            0 to "vless-ws-tls-70899053.bin",
            1 to "vmess-ws-tls-70899053.bin",
            2 to "trojan-tls-70899053.bin",
            3 to "shadowsocks-70899053.bin",
        )
    }
}
