package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Path
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
    fun `readBytesTruncated caps payload and marks truncation`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)

        val result = BootDiagnostics.readBytesTruncated(payload.inputStream(), maxBytes = 3)

        assertEquals(listOf<Byte>(1, 2, 3), result.bytes.toList())
        assertTrue(result.truncated)
    }

    @Test
    fun `readBytesTruncated does not read past byte cap`() {
        val input = CountingInputStream(byteArrayOf(1, 2, 3, 4, 5))

        val result = BootDiagnostics.readBytesTruncated(input, maxBytes = 3)

        assertEquals(listOf<Byte>(1, 2, 3), result.bytes.toList())
        assertTrue(result.truncated)
        assertEquals(1, input.readCalls)
    }

    @Test
    fun `readBytesTruncated reports no truncation when EOF arrives before cap`() {
        val payload = byteArrayOf(1, 2, 3)

        val result = BootDiagnostics.readBytesTruncated(payload.inputStream(), maxBytes = 8)

        assertEquals(payload.toList(), result.bytes.toList())
        assertFalse(result.truncated)
    }

    @Test
    fun `readTextTruncated caps payload and marks truncation`() {
        val reader = BufferedReader(StringReader("abcdef"))

        val result = BootDiagnostics.readTextTruncated(reader, maxChars = 4)

        assertEquals("abcd", result.text)
        assertTrue(result.truncated)
    }

    @Test
    fun `readTextTruncated does not read past char cap`() {
        val reader = CountingBufferedReader("abcdef")

        val result = BootDiagnostics.readTextTruncated(reader, maxChars = 4)

        assertEquals("abcd", result.text)
        assertTrue(result.truncated)
        assertEquals(1, reader.readCalls)
    }

    @Test
    fun `readTextTruncated reports no truncation when EOF arrives before cap`() {
        val reader = BufferedReader(StringReader("abc"))

        val result = BootDiagnostics.readTextTruncated(reader, maxChars = 8)

        assertEquals("abc", result.text)
        assertFalse(result.truncated)
    }

    @Test
    fun `dumpExitReasons CRASH_NATIVE branch saves tombstone file without dumping symbols`() {
        val src = readSelfSource()
        val nativeBranch = src.substringAfter("REASON_CRASH_NATIVE)").substringBefore("else if (info.reason")
        assertContains(nativeBranch, "saveTombstone(", message = "CRASH_NATIVE ветка обязана сохранять бинарь")
        assertTrue(
            !nativeBranch.contains("extractAsciiStrings("),
            "tombstone symbols НЕ должны дампиться в лог — файл сохранён, символы раздувают лог в 100x",
        )
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
        assertContains(textBranch, "BufferedReader", message = "JVM/ANR трейсы — текст, должен читаться построчно")
    }

    private fun readSelfSource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/logging/BootDiagnostics.kt")
        check(f.exists()) { "BootDiagnostics.kt не найден: $f" }
        return f.readText()
    }

    private class CountingInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {
        var readCalls: Int = 0
            private set
        private var offset: Int = 0

        override fun read(): Int {
            readCalls += 1
            if (offset >= bytes.size) return -1
            return bytes[offset++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            readCalls += 1
            if (offset >= bytes.size) return -1
            val count = minOf(len, bytes.size - offset)
            bytes.copyInto(buffer, off, offset, offset + count)
            offset += count
            return count
        }
    }

    private class CountingBufferedReader(text: String) : BufferedReader(StringReader(text)) {
        var readCalls: Int = 0
            private set

        override fun read(buffer: CharArray, off: Int, len: Int): Int {
            readCalls += 1
            return super.read(buffer, off, len)
        }

        override fun read(): Int {
            readCalls += 1
            return super.read()
        }
    }
}
