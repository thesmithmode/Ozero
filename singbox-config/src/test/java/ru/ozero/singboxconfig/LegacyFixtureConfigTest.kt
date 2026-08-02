package ru.ozero.singboxconfig

import org.json.JSONObject
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.KryoSerializer
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LegacyFixtureConfigTest {
    @Test
    fun `golden legacy fixtures produce complete sing-box configs`() {
        FIXTURES.forEach { name ->
            val bytes = assertNotNull(javaClass.getResourceAsStream("/legacy/$name")).use { it.readBytes() }
            val bean = KryoSerializer.deserializeWithMigration(bytes).bean
            val config = JSONObject(ConfigBuilder.buildSingboxConfig(bean))

            assertTrue(config.getJSONArray("outbounds").length() > 0, name)
            assertTrue(config.getJSONArray("inbounds").length() > 0, name)
        }
    }

    private companion object {
        val FIXTURES = listOf(
            "vless-reality-vision-70899053.bin",
            "vless-ws-tls-70899053.bin",
            "vmess-ws-tls-70899053.bin",
            "trojan-tls-70899053.bin",
            "shadowsocks-70899053.bin",
        )
    }
}
