package ru.ozero.app.ui.onboarding

import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootstrapServersAssetTest {

    private val asset = File("src/main/assets/bootstrap-servers.json")

    @Test
    fun `asset exists`() {
        assertTrue(asset.exists(), "bootstrap-servers.json должен существовать в assets")
    }

    @Test
    fun `parses as valid JSON with servers array`() {
        val json = JSONObject(asset.readText())
        val servers = json.optJSONArray("servers")
        assertNotNull(servers, "servers array обязателен")
        assertEquals(0, servers!!.length(), "bundled asset не должен содержать live proxy credentials")
    }

    @Test
    fun `no placeholder URIs`() {
        val json = JSONObject(asset.readText())
        val servers = json.optJSONArray("servers")!!
        for (i in 0 until servers.length()) {
            val uri = servers.optString(i)
            assertFalse(uri.contains("placeholder"), "URI #$i содержит 'placeholder': $uri")
            assertFalse(uri.contains("example.invalid"), "URI #$i содержит example.invalid: $uri")
        }
    }

    @Test
    fun `all URIs use supported schemes`() {
        val json = JSONObject(asset.readText())
        val servers = json.optJSONArray("servers")!!
        val supported = setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")
        for (i in 0 until servers.length()) {
            val uri = servers.optString(i)
            val scheme = uri.substringBefore("://").lowercase()
            assertTrue(scheme in supported, "URI #$i scheme '$scheme' не поддерживается: $uri")
        }
    }

    @Test
    fun `asset does not ship live proxy credentials or insecure proxy flags`() {
        val text = asset.readText()
        listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://").forEach { scheme ->
            assertFalse(text.contains(scheme), "bundled bootstrap не должен содержать live proxy URI: $scheme")
        }
        listOf("allowInsecure=1", "security=none", "insecure=1", "encryption=none").forEach { marker ->
            assertFalse(text.contains(marker), "bundled bootstrap не должен содержать insecure marker: $marker")
        }
    }

    @Test
    fun `snapshot metadata fields present`() {
        val json = JSONObject(asset.readText())
        assertNotNull(json.optString("_snapshot_date").takeIf { it.isNotEmpty() }, "_snapshot_date обязателен")
        assertNotNull(json.optString("_snapshot_source").takeIf { it.isNotEmpty() }, "_snapshot_source обязателен")
    }
}
