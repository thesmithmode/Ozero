package ru.ozero.engineopera

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

internal object OperaDnsResponse {
    private const val MAX_LINE = 4096
    private const val MAX_HEADERS = 32
    private const val DNS_HEADER = 12

    fun validate(source: InputStream) {
        val input = BufferedInputStream(source)
        val status = line(input).split(' ')
        if (status.size < 2 || !status[0].startsWith("HTTP/") || status[1] != "200") {
            throw IOException("Opera DoH HTTP response rejected")
        }
        val headers = readHeaders(input)
        if (headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/dns-message") {
            throw IOException("Opera DoH content type rejected")
        }
        if (headers["transfer-encoding"] != null) throw IOException("Unexpected DoH transfer encoding")
        val length = headers["content-length"]?.toIntOrNull()
        if (length != null && length < DNS_HEADER) throw IOException("Truncated DoH response")
        val header = IntArray(DNS_HEADER) {
            input.read().takeIf { value -> value >= 0 } ?: throw IOException("Truncated DoH DNS header")
        }
        if (header[0] != 0 || header[1] != 0 || header[2] and 0x82 != 0x80 ||
            header[3] and 0x0f != 0 || header[4] != 0 || header[5] != 1 ||
            (header[6] == 0 && header[7] == 0)
        ) {
            throw IOException("Opera DoH DNS answer rejected")
        }
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        repeat(MAX_HEADERS) {
            val header = line(input)
            if (header.isEmpty()) return headers
            val separator = header.indexOf(':')
            if (separator <= 0) throw IOException("Invalid DoH header")
            headers[header.substring(0, separator).lowercase()] = header.substring(separator + 1).trim()
        }
        throw IOException("Too many DoH headers")
    }

    private fun line(input: InputStream): String {
        val result = StringBuilder()
        repeat(MAX_LINE) {
            when (val next = input.read()) {
                -1 -> throw IOException("Truncated DoH HTTP response")
                10 -> return result.toString().trimEnd('\r')
                else -> result.append(next.toChar())
            }
        }
        throw IOException("DoH header line too long")
    }
}
