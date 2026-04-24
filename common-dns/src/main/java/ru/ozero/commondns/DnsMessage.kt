package ru.ozero.commondns

import java.io.ByteArrayOutputStream

internal object DnsMessage {

    fun buildAQuery(hostname: String): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(byteArrayOf(0x12, 0x34)) // id
        buf.write(byteArrayOf(0x01, 0x00)) // flags: standard query, recursion desired
        buf.write(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0)) // qd=1 an=0 ns=0 ar=0
        hostname.split(".").forEach { label ->
            buf.write(label.length)
            buf.write(label.toByteArray())
        }
        buf.write(0)
        buf.write(byteArrayOf(0, 1, 0, 1)) // qtype=A qclass=IN
        return buf.toByteArray()
    }

    fun parseAAnswers(body: ByteArray): List<String> {
        if (body.size < 12) return emptyList()
        val anCount = ((body[6].toInt() and 0xFF) shl 8) or (body[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var offset = 12
        // skip question section (qname + qtype(2) + qclass(2))
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
            if (type == 1 && rdLength == 4) {
                val ip =
                    "${body[offset].toInt() and 0xFF}." +
                        "${body[offset + 1].toInt() and 0xFF}." +
                        "${body[offset + 2].toInt() and 0xFF}." +
                        "${body[offset + 3].toInt() and 0xFF}"
                result += ip
            }
            offset += rdLength
        }
        return result
    }

    private fun skipName(body: ByteArray, start: Int): Int {
        var offset = start
        while (offset < body.size) {
            val len = body[offset].toInt() and 0xFF
            when {
                len == 0 -> return offset + 1
                len and 0xC0 == 0xC0 -> {
                    // Compression pointer занимает 2 байта — проверяем что второй доступен.
                    if (offset + 1 >= body.size) return body.size
                    return offset + 2
                }
                else -> offset += len + 1
            }
        }
        return offset
    }
}
