package ru.ozero.enginegfvpn

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GfVpnCaptchaStateTest {
    private val owner = Any()
    private val replacement = Any()

    @AfterEach
    fun cleanUp() {
        GfVpnCaptchaState.clear(owner)
        GfVpnCaptchaState.clear(replacement)
    }

    @Test
    fun `only explicit local challenge origins are accepted and URL is redacted`() {
        val invalid = listOf(
            "https://example.com:443/",
            "http://127.0.0.1.example.com:123/",
            "http://user@127.0.0.1:123/",
            "file:///data/local/tmp/",
            "http://localhost/",
            "http://127.0.0.1:65536/",
            "http://127.0.0.1:12/%",
            "x".repeat(8193),
        )
        invalid.forEach { assertFalse(GfVpnCaptchaState.publish(owner, it, Runnable {})) }
        assertNull(GfVpnCaptchaState.challenge.value)
        assertTrue(GfVpnCaptchaState.publish(owner, "http://127.0.0.1:1234/challenge?secret=x", Runnable {}))
        val current = requireNotNull(GfVpnCaptchaState.challenge.value)
        assertTrue(current.permits("http://127.0.0.1:1234/result"))
        assertFalse(current.permits("http://127.0.0.1:1235/"))
        assertFalse(current.permits("https://127.0.0.1:1234/"))
        assertFalse(current.permits("https://example.com:443/"))
        assertFalse(current.toString().contains("secret"))
        assertTrue(GfVpnCaptchaState.publish(owner, "http://localhost:1234/", Runnable {}))
    }

    @Test
    fun `stale owner and stale dismissal cannot clear the new session`() {
        var cancellations = 0
        assertTrue(GfVpnCaptchaState.publish(owner, "http://localhost:1234/", Runnable { cancellations++ }))
        val stale = requireNotNull(GfVpnCaptchaState.challenge.value)
        assertTrue(GfVpnCaptchaState.publish(replacement, "http://localhost:1235/", Runnable { cancellations++ }))
        GfVpnCaptchaState.clear(owner)
        GfVpnCaptchaState.cancel(stale)
        assertNotNull(GfVpnCaptchaState.challenge.value)
        assertEquals(0, cancellations)
        val current = requireNotNull(GfVpnCaptchaState.challenge.value)
        GfVpnCaptchaState.cancel(current)
        GfVpnCaptchaState.cancel(current)
        assertEquals(1, cancellations)
        assertNull(GfVpnCaptchaState.challenge.value)
    }

    @Test
    fun `duplicate callbacks do not reset the in-progress challenge`() {
        val url = "http://localhost:1234/"
        assertTrue(GfVpnCaptchaState.publish(owner, url, Runnable {}))
        val current = GfVpnCaptchaState.challenge.value
        assertTrue(GfVpnCaptchaState.publish(owner, url, Runnable {}))
        assertSame(current, GfVpnCaptchaState.challenge.value)
        assertFalse(GfVpnCaptchaState.clear(replacement))
        assertTrue(GfVpnCaptchaState.clear(owner))
        assertFalse(GfVpnCaptchaState.clear(owner))
    }

    @Test
    fun `verification wait is bounded and scoped to the current owner`() = runBlocking {
        assertTrue(GfVpnCaptchaState.publish(owner, "http://localhost:1234/", Runnable {}))
        assertTrue(GfVpnCaptchaState.awaitResolution(replacement, 100L))
        assertFalse(GfVpnCaptchaState.awaitResolution(owner, 10L))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            GfVpnCaptchaState.awaitResolution(owner, 1000L)
        }
        assertFalse(waiting.isCompleted)
        GfVpnCaptchaState.clear(owner)
        assertTrue(waiting.await())
    }

    @Test
    fun `resolved event closes its challenge without invoking cancellation`() {
        var cancelled = false
        assertTrue(GfVpnCaptchaState.publish(owner, "http://localhost:1234/", Runnable { cancelled = true }))
        GfVpnCaptchaState.clear(owner)
        assertNull(GfVpnCaptchaState.challenge.value)
        assertFalse(cancelled)
    }
}
