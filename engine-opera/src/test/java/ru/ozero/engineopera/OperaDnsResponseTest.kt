package ru.ozero.engineopera

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class OperaDnsResponseTest {
    private val answer = byteArrayOf(0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
    private val headers = "HTTP/1.0 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 12\r\n\r\n"

    @Test
    fun `accepts valid DNS response over HTTPS`() {
        OperaSocksProbe.validateDnsResponse(response(headers, answer))
    }

    @Test
    fun `rejects invalid HTTP status headers and DNS answer`() {
        val invalid = listOf(
            response(headers.replace("200 OK", "403 Forbidden"), answer),
            response(headers.replace("application/dns-message", "text/html"), answer),
            response(headers.replace("Content-Length: 12", "Transfer-Encoding: chunked"), answer),
            response(headers.replace("Content-Length: 12", "Content-Length: 10"), answer),
            response(headers, byteArrayOf(0)),
            response(headers, answer.copyOf().apply { this[1] = 1 }),
            response(headers, answer.copyOf().apply { this[2] = 0 }),
            response(headers, answer.copyOf().apply { this[3] = 2 }),
            response(headers, answer.copyOf().apply { this[5] = 0 }),
            response(headers, answer.copyOf().apply { this[7] = 0 }),
            response("HTTP/1.0 200 OK\r\nbad header\r\n\r\n", answer),
            response("HTTP/1.0 200 OK\r\n" + "x: y\r\n".repeat(32), answer),
            response("x".repeat(4097), answer),
            response("", byteArrayOf()),
        )
        invalid.forEach { input ->
            assertThrows(IOException::class.java) { OperaSocksProbe.validateDnsResponse(input) }
        }
    }

    private fun response(headers: String, body: ByteArray) =
        ByteArrayInputStream(headers.toByteArray(Charsets.US_ASCII) + body)
}
