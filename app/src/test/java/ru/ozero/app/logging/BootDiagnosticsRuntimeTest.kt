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
}
