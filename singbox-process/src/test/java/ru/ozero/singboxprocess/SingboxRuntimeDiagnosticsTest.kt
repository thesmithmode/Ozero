package ru.ozero.singboxprocess

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxRuntimeDiagnosticsTest {
    private val runtimeSource by lazy {
        java.io.File("src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt").readText()
    }

    @Test
    fun `old diagnostic session cannot mutate replacement client`() {
        val guard = NativeDiagnosticsSessionGuard()
        val oldClient = Any()
        val oldGeneration = guard.begin()
        val newClient = Any()
        val newGeneration = guard.begin()

        assertFalse(guard.isCurrent(oldGeneration, oldClient, newClient))
        assertTrue(guard.isCurrent(newGeneration, newClient, newClient))
        assertFalse(guard.claimReconnect(oldGeneration))
    }

    @Test
    fun `diagnostic reconnect can be claimed only once per generation`() {
        val guard = NativeDiagnosticsSessionGuard()
        val generation = guard.begin()

        assertTrue(guard.claimReconnect(generation))
        assertFalse(guard.claimReconnect(generation))
        assertFalse(guard.claimReconnect(guard.begin() - 1))
    }

    @Test
    fun `invalidating diagnostic session rejects delayed callbacks`() {
        val guard = NativeDiagnosticsSessionGuard()
        val client = Any()
        val generation = guard.begin()

        guard.invalidate()

        assertFalse(guard.isActive(generation))
        assertFalse(guard.isCurrent(generation, client, client))
        assertFalse(guard.claimReconnect(generation))
    }

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
    fun `redaction removes every supported secret shape`() {
        val secrets = listOf(
            "username=user-value",
            "token='token-value'",
            "authorization: bearer-value",
            "cookie=cookie-value",
            "short_id=short-value",
            "server_name=name-value",
            "host=host-value",
            "SNI=sni-value",
            "headers=header-value",
            "https://url-user:url-password@subscription.example/path?token=url-token#fragment",
        )

        val redacted = redactSingboxMessage(secrets.joinToString(" "))

        listOf(
            "user-value",
            "token-value",
            "bearer-value",
            "cookie-value",
            "short-value",
            "name-value",
            "host-value",
            "sni-value",
            "header-value",
            "url-user",
            "url-password",
            "url-token",
            "fragment",
        ).forEach { secret -> assertFalse(redacted.contains(secret)) }
    }

    @Test
    fun `libbox diagnostic keywords are promoted`() {
        assertTrue("connection closed".shouldPromoteSingboxMessage())
        assertTrue("TLS handshake failed".shouldPromoteSingboxMessage())
        assertFalse("started listener".shouldPromoteSingboxMessage())
    }

    @Test
    fun `native log categories discard raw endpoint and credentials`() {
        assertEquals(
            "reality-handshake",
            nativeLogCategory(
                "reality handshake failed server=private.example uuid=12345678-1234-1234-1234-123456789abc",
            ),
        )
        assertEquals("tls-certificate", nativeLogCategory("TLS certificate rejected for private.example"))
        assertEquals("remote-closed", nativeLogCategory("upstream EOF from private.example"))
        assertEquals(null, nativeLogCategory("listener started at private.example"))
    }

    @Test
    fun `native log classification prioritizes terminal transport state`() {
        assertEquals("remote-closed", nativeLogCategory("connection closed"))
        assertEquals("connect", nativeLogCategory("connect: connection refused"))
        assertEquals("dial", nativeLogCategory("dial tcp: timeout"))
        assertEquals("network-unavailable", nativeLogCategory("network unavailable"))
        assertEquals("remote-closed", nativeLogCategory("write: broken pipe"))
    }

    @Test
    fun `diagnostics starts only after runtime becomes available`() {
        val serviceStart = runtimeSource.indexOf("server.startOrReloadService")
        val serverClaim = runtimeSource.indexOf("commandServer = server", serviceStart)
        val diagnosticsLaunch = runtimeSource.indexOf("launchNativeLogSubscription(failureDiagnostics)", serverClaim)

        assertTrue(serviceStart in 0..<serverClaim)
        assertTrue(serverClaim in 0..<diagnosticsLaunch)
    }

    @Test
    fun `diagnostic connect is supervised and time bounded`() {
        assertContains(runtimeSource, "CoroutineScope(SupervisorJob() + Dispatchers.IO)")
        assertContains(runtimeSource, "withTimeout(NATIVE_LOG_CONNECT_TIMEOUT_MS)")
        assertContains(runtimeSource, "runInterruptible { client.connect() }")
        assertContains(runtimeSource, "native diagnostics unavailable exceptionClass=")
    }

    @Test
    fun `native failure diagnostics preserve protect as the dominant failure`() {
        val emitted = mutableListOf<String>()
        val diagnostics = NativeFailureDiagnostics(emitted::add)

        diagnostics.recordNative("connect refused outbound primary server=private.example")
        diagnostics.recordNative("connect refused outbound primary server=private.example")
        diagnostics.recordNative("active VPN protect failed")
        diagnostics.recordNative("dns resolve failed outbound resolver")

        assertEquals(3, emitted.size)
        assertContains(emitted[0], "outbound=primary category=CONNECT dominant=CONNECT")
        assertContains(emitted[1], "outbound=unknown category=PROTECT_FAILED dominant=PROTECT_FAILED")
        assertContains(emitted[2], "outbound=resolver category=DNS dominant=PROTECT_FAILED")
        assertFalse(emitted[0].contains("private.example"))
    }

    @Test
    fun `native failure categories cover each actionable class`() {
        val cases = listOf(
            "protect failed" to NativeFailureCategory.PROTECT_FAILED,
            "default interface unavailable" to NativeFailureCategory.DEFAULT_INTERFACE,
            "dns resolve failed" to NativeFailureCategory.DNS,
            "reality handshake failed" to NativeFailureCategory.REALITY_HANDSHAKE,
            "tls handshake failed" to NativeFailureCategory.TLS,
            "connect refused" to NativeFailureCategory.CONNECT,
            "upstream EOF" to NativeFailureCategory.REMOTE_CLOSED,
            "request timeout" to NativeFailureCategory.TIMEOUT,
        )

        cases.forEach { (message, category) -> assertEquals(category, nativeFailureCategory(message)) }
        assertEquals(null, nativeFailureCategory("listener started"))
    }

    @Test
    fun `native failure diagnostics stay bounded per session`() {
        val emitted = mutableListOf<String>()
        val diagnostics = NativeFailureDiagnostics(emitted::add)

        repeat(17) { index ->
            diagnostics.record(NativeFailureCategory.CONNECT, "outbound-$index", "connect failed")
        }

        assertEquals(16, emitted.size)
    }

    @Test
    fun `restart and stop close diagnostics`() {
        val restart = runtimeSource.substringAfter("if (oldServer != null)").substringBefore("val socketFile")
        val stop = runtimeSource.substringAfter("suspend fun stop()").substringBefore("fun isRunning()")

        assertContains(restart, "stopNativeLogSubscription()")
        assertContains(stop, "stopNativeLogSubscription()")
        assertContains(runtimeSource, "nativeLogJob?.cancelAndJoin()")
        assertContains(runtimeSource, "client.disconnect()")
    }
}
