package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSanitizerTest {

    @Test
    fun `sanitize redacts userinfo proxy uri and long tokens`() {
        val raw = "socks5://user:pass@proxy.example.com vless://secret@example.com/path token=abcdefghijklmnopqrstuvwxyzABCDEF"

        val sanitized = LogSanitizer.sanitize(raw)

        assertTrue(sanitized.contains("socks5://<redacted>@proxy.example.com"))
        assertTrue(sanitized.contains("<redacted-uri>"))
        assertTrue(sanitized.contains("token=<redacted-token>"))
        assertFalse(sanitized.contains("user:pass"))
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyzABCDEF"))
    }

    @Test
    fun `sanitize keeps short ordinary text unchanged`() {
        assertEquals("engine started port=1080", LogSanitizer.sanitize("engine started port=1080"))
    }

    @Test
    fun `redactUrl keeps scheme host and port only`() {
        assertEquals(
            "https://example.com:8443/<redacted>",
            LogSanitizer.redactUrl("https://user:pass@example.com:8443/path?q=secret"),
        )
    }

    @Test
    fun `redactUrl handles missing host missing scheme and malformed url`() {
        assertEquals("file://<redacted>", LogSanitizer.redactUrl("file:///tmp/secret"))
        assertEquals("<redacted-uri>", LogSanitizer.redactUrl("://broken"))
    }
}
