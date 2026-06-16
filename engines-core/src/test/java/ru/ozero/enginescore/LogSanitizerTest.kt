package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSanitizerTest {

    @Test
    fun `sanitize redacts userinfo proxy uri and long tokens`() {
        val raw = listOf(
            "socks5://user:pass@proxy.example.com",
            "vless://secret@example.com/path",
            "token=abcdefghijklmnopqrstuvwxyzABCDEF",
        ).joinToString(" ")

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
    fun `sanitize preserves long class and method names`() {
        val raw = """
            EngineRuntimeConfigRestartObserver failed
            at ru.ozero.app.vpn.EngineRuntimeConfigRestartObserver.observeRuntimeConfigChanges(EngineRuntimeConfigRestartObserver.kt:42)
            token=abcdefghijklmnopqrstuvwxyzABCDEF0123456789
        """.trimIndent()

        val sanitized = LogSanitizer.sanitize(raw)

        assertTrue(sanitized.contains("EngineRuntimeConfigRestartObserver"))
        assertTrue(sanitized.contains("observeRuntimeConfigChanges"))
        assertTrue(sanitized.contains("token=<redacted-token>"))
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyzABCDEF0123456789"))
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
        assertEquals("<redacted-uri>", LogSanitizer.redactUrl("example.com/path"))
        assertEquals("<redacted-uri>", LogSanitizer.redactUrl("://broken"))
    }

    @Test
    fun `redactUrl redacts urls without host and preserves scheme only`() {
        assertEquals("urn://<redacted>", LogSanitizer.redactUrl("urn:secret:value"))
        assertEquals("https://example.com/<redacted>", LogSanitizer.redactUrl("https://example.com/path"))
    }

    @Test
    fun `sanitize redacts keyed and bare long tokens with supported alphabet`() {
        val raw = "jwt=abcdefghijklmnopqrstuvwxyzABCDEF0123456789 bare=abcdEFGHijklMNOPqrstUVWXyz012345"
        val sanitized = LogSanitizer.sanitize(raw)

        assertTrue(sanitized.contains("jwt=<redacted-token>"))
        assertTrue(sanitized.contains("bare=<redacted-token>"))
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyzABCDEF0123456789"))
        assertFalse(sanitized.contains("abcdEFGHijklMNOPqrstUVWXyz012345"))
    }

    @Test
    fun `sanitize preserves long alphabetic identifiers but redacts digit or separator tokens`() {
        val identifier = "EngineRuntimeConfigRestartObserverLongIdentifier"
        val withDigit = "abcdefghijklmnopqrstuvwxyzABCDEF012345"
        val withSeparator = "abcdefghijklmnopqrstuvwxyzABCDEF_ghijkl"

        val sanitized = LogSanitizer.sanitize("$identifier $withDigit $withSeparator")

        assertTrue(sanitized.contains(identifier))
        assertEquals(2, Regex("<redacted-token>").findAll(sanitized).count())
        assertFalse(sanitized.contains(withDigit))
        assertFalse(sanitized.contains(withSeparator))
    }

    @Test
    fun `sanitize redacts separator-only long token variants`() {
        val tokens = listOf(
            "abcdefghijklmnopqrstuvwxyzABCDEF+",
            "abcdefghijklmnopqrstuvwxyzABCDEF/",
            "abcdefghijklmnopqrstuvwxyzABCDEF-",
            "abcdefghijklmnopqrstuvwxyzABCDEF=",
        )

        val sanitized = LogSanitizer.sanitize(tokens.joinToString(" "))

        assertEquals(tokens.size, Regex("<redacted-token>").findAll(sanitized).count())
        tokens.forEach { assertFalse(sanitized.contains(it)) }
    }

    @Test
    fun `sanitize redacts long alphabetic keyed values`() {
        val value = "abcdefghijklmnopqrstuvwxyzABCDEFGH"

        val sanitized = LogSanitizer.sanitize("class=$value")

        assertEquals("class=<redacted-token>", sanitized)
    }
}
