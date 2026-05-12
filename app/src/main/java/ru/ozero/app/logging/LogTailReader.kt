package ru.ozero.app.logging

import java.io.File
import java.io.RandomAccessFile

internal object LogTailReader {

    fun read(current: File?, prev: File?): String {
        val cur = current?.takeIf { it.exists() }?.readText().orEmpty()
        val p = prev?.takeIf { it.exists() }?.readText().orEmpty()
        return p + cur
    }

    fun readTail(current: File?, prev: File?, maxBytes: Long): String {
        val f = current ?: return ""
        if (!f.exists()) return ""
        val curLen = f.length()
        val p = prev?.takeIf { it.exists() }
        if (p == null) return tailOf(f, curLen, maxBytes)
        if (curLen >= maxBytes) return tailOf(f, curLen, maxBytes)
        val needFromPrev = maxBytes - curLen
        val prevPart = tailOf(p, p.length(), needFromPrev)
        val currentPart = f.readText()
        return prevPart + currentPart
    }

    private fun tailOf(file: File, len: Long, maxBytes: Long): String {
        if (len <= maxBytes) return runCatching { file.readText() }.getOrDefault("")
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(len - maxBytes)
                val buf = ByteArray(maxBytes.toInt())
                val read = raf.read(buf)
                if (read <= 0) "" else String(buf, 0, read, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }
}
