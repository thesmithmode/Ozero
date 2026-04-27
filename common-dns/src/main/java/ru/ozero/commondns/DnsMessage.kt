package ru.ozero.commondns

import java.io.ByteArrayOutputStream
import java.security.SecureRandom

internal object DnsMessage {

    private val random = SecureRandom()

    fun buildAQuery(hostname: String): ByteArray = buildQuery(hostname, qtype = 1)

    fun buildAAAAQuery(hostname: String): ByteArray = buildQuery(hostname, qtype = 28)

    private fun buildQuery(hostname: String, qtype: Int): ByteArray {
        val buf = ByteArrayOutputStream()
                                val id = ByteArray(2)
        random.nextBytes(id)
        buf.write(id)
        buf.write(byteArrayOf(0x01, 0x00)) 
        buf.write(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0)) 
        hostname.split(".").forEach { label ->
            buf.write(label.length)
            buf.write(label.toByteArray())
        }
        buf.write(0)
        buf.write(byteArrayOf(0, (qtype and 0xFF).toByte(), 0, 1)) 
        return buf.toByteArray()
    }

    fun parseAAnswers(body: ByteArray): List<String> = parseAnswers(body, expectType = 1, expectRdLen = 4)

    fun parseAAAAAnswers(body: ByteArray): List<String> = parseAnswers(body, expectType = 28, expectRdLen = 16)

    private fun parseAnswers(body: ByteArray, expectType: Int, expectRdLen: Int): List<String> {
        if (body.size < 12) return emptyList()
        val anCount = ((body[6].toInt() and 0xFF) shl 8) or (body[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var offset = 12
                offset = skipName(body, offset) + 4

        val result = mutableListOf<String>()
        repeat(anCount) {
            if (offset >= body.size) return result
            offset = skipName(body, offset)
            if (offset + 10 > body.size) return result
            val type = ((body[offset].toInt() and 0xFF) shl 8) or (body[offset + 1].toInt() and 0xFF)
            val rdLength = ((body[offset + 8].toInt() and 0xFF) shl 8) or (body[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + rdLength > body.size) return result
            if (type == expectType && rdLength == expectRdLen) {
                result += if (expectType == 1) formatIpv4(body, offset) else formatIpv6(body, offset)
            }
            offset += rdLength
        }
        return result
    }

    private fun formatIpv4(body: ByteArray, off: Int): String =
        "${body[off].toInt() and 0xFF}." +
            "${body[off + 1].toInt() and 0xFF}." +
            "${body[off + 2].toInt() and 0xFF}." +
            "${body[off + 3].toInt() and 0xFF}"

    private fun formatIpv6(body: ByteArray, off: Int): String {
        val sb = StringBuilder(39)
        for (i in 0 until 8) {
            if (i > 0) sb.append(':')
            val hi = body[off + i * 2].toInt() and 0xFF
            val lo = body[off + i * 2 + 1].toInt() and 0xFF
            sb.append(Integer.toHexString((hi shl 8) or lo))
        }
        return sb.toString()
    }

    private fun skipName(body: ByteArray, start: Int): Int {
        var offset = start
                var iter = 0
        while (offset < body.size) {
            if (iter++ > MAX_LABEL_ITER) return body.size
            val len = body[offset].toInt() and 0xFF
            when {
                len == 0 -> return offset + 1
                len and 0xC0 == 0xC0 -> {
                                        if (offset + 1 >= body.size) return body.size
                    return offset + 2
                }
                else -> offset += len + 1
            }
        }
        return offset
    }

    private const val MAX_LABEL_ITER = 128
}
