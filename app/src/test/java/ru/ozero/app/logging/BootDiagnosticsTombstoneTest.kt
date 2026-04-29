package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
    fun `saveTombstone writes raw bytes verbatim and returns file path`(@TempDir tmp: Path) {
        val debugDir = tmp.toFile()
        val payload = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x80.toByte(), 0xFF.toByte())

        val saved = BootDiagnostics.saveTombstone(debugDir, pid = 27683, timestamp = 1777457944245L, bytes = payload)

        assertTrue(saved.exists(), "файл должен быть создан")
        assertEquals(payload.toList(), saved.readBytes().toList(), "содержимое должно быть бинарно идентичным")
        assertContains(saved.name, "27683")
        assertContains(saved.name, "1777457944245")
        assertTrue(saved.name.endsWith(".pb"))
    }

    @Test
    fun `saveTombstone creates debug dir if missing`(@TempDir tmp: Path) {
        val debugDir = File(tmp.toFile(), "debug-not-yet")
        assertTrue(!debugDir.exists())

        val saved = BootDiagnostics.saveTombstone(debugDir, pid = 1, timestamp = 2L, bytes = byteArrayOf(0x42))

        assertTrue(debugDir.isDirectory)
        assertTrue(saved.exists())
    }

    @Test
    fun `dumpExitReasons CRASH_NATIVE branch использует saveTombstone и extractAsciiStrings`() {
        val src = readSelfSource()
        val nativeBranch = src.substringAfter("REASON_CRASH_NATIVE)").substringBefore("else if (info.reason")
        assertContains(nativeBranch, "saveTombstone(", "CRASH_NATIVE ветка обязана сохранять бинарь")
        assertContains(nativeBranch, "extractAsciiStrings(", "CRASH_NATIVE ветка обязана извлекать читаемые ASCII")
        assertTrue(
            !nativeBranch.contains("BufferedReader"),
            "CRASH_NATIVE НЕ должен читать через BufferedReader — это бинарный protobuf, выход — каракули",
        )
    }

    @Test
    fun `dumpExitReasons CRASH_JVM и ANR используют BufferedReader (текстовый трейс)`() {
        val src = readSelfSource()
        val textBranch = src.substringAfter("REASON_ANR ||").substringBefore("if (info.reason == ApplicationExitInfo.REASON_SIGNALED)")
        assertContains(textBranch, "BufferedReader", "JVM/ANR трейсы — текст, должен читаться построчно")
    }

    private fun readSelfSource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/logging/BootDiagnostics.kt")
        check(f.exists()) { "BootDiagnostics.kt не найден: $f" }
        return f.readText()
    }
}
