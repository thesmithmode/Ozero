package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootDiagnosticsTombstoneTest {

    @Test
    fun `extractAsciiStrings returns symbol names from binary tombstone blob`() {
        val blob = byteArrayOf(
            0x08, 0x01, 0x12, 0x0A,
        ) + "libbyedpi.so".toByteArray() + byteArrayOf(0x00, 0x01, 0x02) +
            "art::Thread::Park".toByteArray() + byteArrayOf(0x00) +
            "DefaultDispatcher-worker-4".toByteArray() + byteArrayOf(0x7F, 0x00) +
            "shrt".toByteArray() + byteArrayOf(0x00) +
            "pthread_start".toByteArray()

        val out = BootDiagnostics.extractAsciiStrings(blob, minLen = 6)

        assertContains(out, "libbyedpi.so")
        assertContains(out, "art::Thread::Park")
        assertContains(out, "DefaultDispatcher-worker-4")
        assertContains(out, "pthread_start")
    }

    @Test
    fun `extractAsciiStrings drops sequences shorter than minLen`() {
        val blob = "ok".toByteArray() + byteArrayOf(0x00) + "longenough".toByteArray()

        val out = BootDiagnostics.extractAsciiStrings(blob, minLen = 6)

        assertEquals("longenough", out.trim())
    }

    @Test
    fun `extractAsciiStrings on empty input yields empty string`() {
        assertEquals("", BootDiagnostics.extractAsciiStrings(ByteArray(0), minLen = 6))
    }

    @Test
    fun `extractAsciiStrings ignores non-printable bytes outside 0x20-0x7E`() {
        val blob = byteArrayOf(0x01, 0x1F, 0x7F.toByte()) + "printable_seq".toByteArray() +
            byteArrayOf(0x80.toByte(), 0xFF.toByte())

        val out = BootDiagnostics.extractAsciiStrings(blob, minLen = 6)

        assertEquals("printable_seq", out.trim())
    }

    @Test
    fun `dumpExitReasons CRASH_NATIVE branch logs bounded sanitized strings only`() {
        val src = readSelfSource()
        val nativeBranch = src.substringAfter("REASON_CRASH_NATIVE)").substringBefore("else if (info.reason")
        assertContains(nativeBranch, "readAtMost(stream, MAX_TRACE_BYTES)")
        assertContains(nativeBranch, "sanitizeTrace(extractAsciiStrings(bytes))")
        assertFalse(nativeBranch.contains("saveTombstone("))
        assertTrue(
            !nativeBranch.contains("BufferedReader"),
            "CRASH_NATIVE НЕ должен читать через BufferedReader — это бинарный protobuf",
        )
    }

    @Test
    fun `dumpExitReasons CRASH_JVM и ANR используют BufferedReader (текстовый трейс)`() {
        val src = readSelfSource()
        val textBranch = src
            .substringAfter("REASON_ANR ||")
            .substringBefore("if (info.reason == ApplicationExitInfo.REASON_SIGNALED)")
        assertContains(
            textBranch,
            "readTraceText(stream)",
            message = "JVM/ANR трейсы должны читаться через bounded sanitizer",
        )
    }

    private fun readSelfSource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/logging/BootDiagnostics.kt")
        check(f.exists()) { "BootDiagnostics.kt не найден: $f" }
        return f.readText()
    }
}
