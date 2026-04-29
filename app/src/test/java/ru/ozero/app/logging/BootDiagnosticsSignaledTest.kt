package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BootDiagnosticsSignaledTest {

    @Test
    fun `SIGKILL maps to symbolic name`() {
        val result = BootDiagnostics.signalToString(9)
        assertEquals("SIGKILL", result)
    }

    @Test
    fun `SIGSEGV maps to symbolic name`() {
        val result = BootDiagnostics.signalToString(11)
        assertEquals("SIGSEGV", result)
    }

    @Test
    fun `unknown signal falls back to numeric form`() {
        val result = BootDiagnostics.signalToString(12345)
        assertEquals("signal=12345", result)
    }

    @Test
    fun `common signals map correctly`() {
        assertEquals("SIGHUP", BootDiagnostics.signalToString(1))
        assertEquals("SIGINT", BootDiagnostics.signalToString(2))
        assertEquals("SIGQUIT", BootDiagnostics.signalToString(3))
        assertEquals("SIGABRT", BootDiagnostics.signalToString(6))
        assertEquals("SIGPIPE", BootDiagnostics.signalToString(13))
        assertEquals("SIGTERM", BootDiagnostics.signalToString(15))
        assertEquals("SIGSTOP", BootDiagnostics.signalToString(19))
    }
}
