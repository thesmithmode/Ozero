package ru.ozero.engineurnetwork.auth

internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BASE_58 = 58
    private const val BASE_256 = 256

    private val INDICES = IntArray(BASE_256) { -1 }.apply {
        for (i in ALPHABET.indices) this[ALPHABET[i].code] = i
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) zeros++
        val work = input.copyOf()
        val encoded = CharArray(input.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        while (inputStart < work.size) {
            encoded[--outputStart] = ALPHABET[divmod(work, inputStart, BASE_256, BASE_58).toInt()]
            if (work[inputStart].toInt() == 0) inputStart++
        }
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) outputStart++
        var z = zeros
        while (z-- > 0) encoded[--outputStart] = ALPHABET[0]
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val codes = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i].code
            val digit = if (c < BASE_256) INDICES[c] else -1
            if (digit < 0) error("invalid base58 character at $i")
            codes[i] = digit.toByte()
        }
        var zeros = 0
        while (zeros < codes.size && codes[zeros].toInt() == 0) zeros++
        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < codes.size) {
            decoded[--outputStart] = divmod(codes, inputStart, BASE_58, BASE_256).toByte()
            if (codes[inputStart].toInt() == 0) inputStart++
        }
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return decoded.copyOfRange(outputStart - zeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xff
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
