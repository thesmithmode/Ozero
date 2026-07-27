package ru.ozero.singboxprocess

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxRuntimeDiagnosticsTest {
    @Test
    fun `missing ConnectivityManager fails with process specific error`() {
        val context = mockk<Context>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns null

        val error = assertFailsWith<IllegalStateException> { requireConnectivityManager(context) }

        assertContains(error.message.orEmpty(), "ConnectivityManager unavailable in :engine_singbox process")
    }

    @Test
    fun `redaction removes UUID password and key material`() {
        val message = "dial failed uuid=12345678-1234-1234-1234-123456789abc " +
            "password=password-value private_key=private-value public_key=public-value " +
            "serverAddress=server-address.example server=server.example " +
            "url=https://subscription.example/path?token=query-value {\"server\":\"json-server.example\"}"

        val redacted = redactSingboxMessage(message)

        assertFalse(redacted.contains("12345678-1234-1234-1234-123456789abc"))
        assertFalse(redacted.contains("password-value"))
        assertFalse(redacted.contains("private-value"))
        assertFalse(redacted.contains("public-value"))
        assertFalse(redacted.contains("query-value"))
        assertFalse(redacted.contains("server-address.example"))
        assertFalse(redacted.contains("server.example"))
        assertFalse(redacted.contains("json-server.example"))
        assertContains(redacted, "<redacted-uuid>")
        assertContains(redacted, "<redacted-json>")
    }

    @Test
    fun `libbox diagnostic keywords are promoted`() {
        assertTrue("connection closed".shouldPromoteSingboxMessage())
        assertTrue("TLS handshake failed".shouldPromoteSingboxMessage())
        assertFalse("started listener".shouldPromoteSingboxMessage())
    }
}
