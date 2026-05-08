package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IniSanitizerTest {

    @Test
    fun `sanitize маскирует PrivateKey полностью`() {
        val ini = "PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk="
        val out = IniSanitizer.sanitize(ini)
        assertEquals("PrivateKey = ***", out)
        assertFalse(out.contains("yAnz5TF"))
    }

    @Test
    fun `sanitize маскирует PresharedKey полностью`() {
        val ini = "PresharedKey = secret_psk_value_base64_encoded="
        val out = IniSanitizer.sanitize(ini)
        assertEquals("PresharedKey = ***", out)
    }

    @Test
    fun `sanitize маскирует PublicKey оставляя tail 8 символов для диагностики`() {
        val ini = "PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        val out = IniSanitizer.sanitize(ini)
        assertTrue(out.startsWith("PublicKey = ***"), "должен начинаться с маски: $out")
        assertTrue(out.endsWith("2wPfgyo="), "tail (последние 8 символов) сохраняется для fingerprint: $out")
        assertFalse(out.contains("bmXOC+F1"), "head ключа не должен утекать: $out")
    }

    @Test
    fun `sanitize маскирует Endpoint оставляя порт`() {
        val ini = "Endpoint = engage.cloudflareclient.com:4500"
        val out = IniSanitizer.sanitize(ini)
        assertEquals("Endpoint = ***:4500", out)
        assertFalse(out.contains("cloudflare"))
    }

    @Test
    fun `sanitize маскирует Endpoint IPv4 оставляя порт`() {
        val ini = "Endpoint = 162.159.192.1:51820"
        val out = IniSanitizer.sanitize(ini)
        assertEquals("Endpoint = ***:51820", out)
        assertFalse(out.contains("162.159"))
    }

    @Test
    fun `sanitize Endpoint без порта маскирует целиком`() {
        val ini = "Endpoint = host.example.com"
        val out = IniSanitizer.sanitize(ini)
        assertEquals("Endpoint = ***", out)
    }

    @Test
    fun `sanitize не трогает другие поля`() {
        val ini = """
            [Interface]
            PrivateKey = secret123==
            Address = 10.0.0.2/32
            MTU = 1280
            DNS = 1.1.1.1

            [Peer]
            PublicKey = abcdef1234567890==
            Endpoint = 1.2.3.4:51820
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 25
        """.trimIndent()
        val out = IniSanitizer.sanitize(ini)
        assertTrue(out.contains("[Interface]"))
        assertTrue(out.contains("[Peer]"))
        assertTrue(out.contains("Address = 10.0.0.2/32"))
        assertTrue(out.contains("MTU = 1280"))
        assertTrue(out.contains("DNS = 1.1.1.1"))
        assertTrue(out.contains("AllowedIPs = 0.0.0.0/0"))
        assertTrue(out.contains("PersistentKeepalive = 25"))
        assertFalse(out.contains("secret123"), "PrivateKey value не должен утекать")
        assertTrue(out.contains("Endpoint = ***:51820"))
    }

    @Test
    fun `sanitize case-insensitive для имён ключей`() {
        val ini = "privatekey = secret\nENDPOINT = 1.2.3.4:443"
        val out = IniSanitizer.sanitize(ini)
        assertFalse(out.contains("secret"))
        assertFalse(out.contains("1.2.3.4"))
    }
}
