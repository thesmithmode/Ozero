package ru.ozero.corebackup

internal object Base64Text {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val inverse = IntArray(256) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c -> table[c.code] = index }
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xff
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xff else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xff else -1
            out.append(ALPHABET[b0 ushr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or ((b1.coerceAtLeast(0)) ushr 4)])
            out.append(if (b1 >= 0) ALPHABET[((b1 and 0x0f) shl 2) or ((b2.coerceAtLeast(0)) ushr 6)] else '=')
            out.append(if (b2 >= 0) ALPHABET[b2 and 0x3f] else '=')
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        val clean = text.filterNot { it.isWhitespace() }
        require(clean.length % 4 == 0) { "bad base64 length" }
        val pad = clean.takeLast(2).count { it == '=' }
        val out = ByteArray((clean.length / 4) * 3 - pad)
        var inPos = 0
        var outPos = 0
        while (inPos < clean.length) {
            val c0 = value(clean[inPos++])
            val c1 = value(clean[inPos++])
            val c2Char = clean[inPos++]
            val c3Char = clean[inPos++]
            val c2 = if (c2Char == '=') 0 else value(c2Char)
            val c3 = if (c3Char == '=') 0 else value(c3Char)
            if (outPos < out.size) out[outPos++] = ((c0 shl 2) or (c1 ushr 4)).toByte()
            if (outPos < out.size) out[outPos++] = (((c1 and 0x0f) shl 4) or (c2 ushr 2)).toByte()
            if (outPos < out.size) out[outPos++] = (((c2 and 0x03) shl 6) or c3).toByte()
        }
        return out
    }

    private fun value(c: Char): Int {
        val v = if (c.code < inverse.size) inverse[c.code] else -1
        require(v >= 0) { "bad base64 char" }
        return v
    }
}
