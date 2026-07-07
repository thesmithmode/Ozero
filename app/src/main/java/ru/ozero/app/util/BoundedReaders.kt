package ru.ozero.app.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun InputStream.readBytesBounded(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val out = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
        if (read == -1) break
        total += read
        if (total > maxBytes) throw IOException("Input is larger than $maxBytes bytes")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

internal fun InputStream.readTextBounded(maxBytes: Int): String =
    readBytesBounded(maxBytes).toString(Charsets.UTF_8)

private const val DEFAULT_BUFFER_SIZE = 8 * 1024
