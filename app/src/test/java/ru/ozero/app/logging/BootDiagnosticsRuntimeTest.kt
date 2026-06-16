package ru.ozero.app.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootDiagnosticsRuntimeTest {

    @AfterEach
    fun tearDown() {
        val field = BootDiagnostics::class.java.getDeclaredField("uncaughtInstalled")
        field.isAccessible = true
        field.setBoolean(null, false)
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun `guard returns block result on success`() {
        val value = BootDiagnostics.guard("ok", default = -1) { 42 }

        assertEquals(42, value)
    }

    @Test
    fun `guard returns default and swallows exception`() {
        val value = BootDiagnostics.guard("boom", default = -1) {
            error("boom")
        }

        assertEquals(-1, value)
    }

    @Test
    fun `guardUnit swallows exception and keeps execution alive`() {
        val counter = AtomicInteger(0)

        BootDiagnostics.guardUnit("unit") {
            counter.incrementAndGet()
            error("boom")
        }

        assertEquals(1, counter.get())
    }

    @Test
    fun `guardUnit runs successful block once`() {
        val counter = AtomicInteger(0)

        BootDiagnostics.guardUnit("unit-ok") {
            counter.incrementAndGet()
        }

        assertEquals(1, counter.get())
    }

    @Test
    fun `installUncaughtHandler forwards throwable to crash sink and previous handler`() {
        val seen = mutableListOf<String>()
        val previous = Thread.UncaughtExceptionHandler { thread, throwable ->
            seen += "prev:${thread.name}:${throwable.message}"
        }
        Thread.setDefaultUncaughtExceptionHandler(previous)

        val crashSink = AtomicInteger(0)
        BootDiagnostics.installUncaughtHandler { thread, throwable ->
            crashSink.incrementAndGet()
            seen += "sink:${thread.name}:${throwable.message}"
        }

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        handler.uncaughtException(Thread("boot"), IllegalStateException("kaboom"))

        assertEquals(1, crashSink.get())
        assertTrue(seen.any { it.startsWith("sink:boot:kaboom") })
        assertTrue(seen.any { it.startsWith("prev:boot:kaboom") })
        assertFalse(seen.isEmpty())
    }

    @Test
    fun `installUncaughtHandler is idempotent and keeps first crash sink`() {
        val seen = AtomicInteger(0)
        BootDiagnostics.installUncaughtHandler { _, _ -> seen.addAndGet(1) }
        BootDiagnostics.installUncaughtHandler { _, _ -> seen.addAndGet(100) }

        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread("boot"), IllegalStateException("boom"))

        assertEquals(1, seen.get())
    }

    @Test
    fun `extractAsciiStrings keeps printable runs at threshold`() {
        val bytes = byteArrayOf(
            0x01,
            'a'.code.toByte(),
            'b'.code.toByte(),
            'c'.code.toByte(),
            0x00,
            '1'.code.toByte(),
            '2'.code.toByte(),
            '3'.code.toByte(),
            '4'.code.toByte(),
            0x7F,
            'x'.code.toByte(),
            'y'.code.toByte(),
            'z'.code.toByte(),
        )

        val strings = BootDiagnostics.extractAsciiStrings(bytes, minLen = 3)

        assertEquals("abc\n1234\nxyz", strings)
    }

    @Test
    fun `extractAsciiStrings returns empty when runs shorter than minimum`() {
        val strings = BootDiagnostics.extractAsciiStrings("ab\u0000cd".toByteArray(), minLen = 3)

        assertEquals("", strings)
    }

    @Test
    fun `signalToString falls back for unknown signal`() {
        assertEquals("signal=99", BootDiagnostics.signalToString(99))
    }

    @Test
    fun `signalToString maps common fatal and stop signals`() {
        assertEquals("SIGHUP", BootDiagnostics.signalToString(1))
        assertEquals("SIGINT", BootDiagnostics.signalToString(2))
        assertEquals("SIGQUIT", BootDiagnostics.signalToString(3))
        assertEquals("SIGABRT", BootDiagnostics.signalToString(6))
        assertEquals("SIGKILL", BootDiagnostics.signalToString(9))
        assertEquals("SIGSEGV", BootDiagnostics.signalToString(11))
        assertEquals("SIGPIPE", BootDiagnostics.signalToString(13))
        assertEquals("SIGTERM", BootDiagnostics.signalToString(15))
        assertEquals("SIGSTOP", BootDiagnostics.signalToString(19))
    }
}
