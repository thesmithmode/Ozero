package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Base58Test {

    @Test
    fun `encode пустой массив возвращает пустую строку`() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test
    fun `decode пустой строки возвращает пустой массив`() {
        assertEquals(0, Base58.decode("").size)
    }

    @Test
    fun `encode одного нулевого байта = 1`() {
        assertEquals("1", Base58.encode(byteArrayOf(0)))
    }

    @Test
    fun `encode hello world bytes = StV1DL6CwTryKyV`() {
        val input = "hello world".toByteArray(Charsets.UTF_8)
        assertEquals("StV1DL6CwTryKyV", Base58.encode(input))
    }

    @Test
    fun `roundtrip 32 байта Ed25519 pubkey - encode_decode эквивалентен оригиналу`() {
        val raw = ByteArray(32) { (it + 1).toByte() }
        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)
        assertEquals(raw.size, decoded.size)
        for (i in raw.indices) assertEquals(raw[i], decoded[i], "byte $i")
    }

    @Test
    fun `encoded pubkey соответствует Solana формату - 32 байта дают 43-44 символа`() {
        val raw = ByteArray(32) { 0xff.toByte() }
        val encoded = Base58.encode(raw)
        assertTrue(encoded.length in 43..44, "ожидаем 43-44 символа, получили ${encoded.length}")
    }

    @Test
    fun `алфавит без 0OIl - leading zero bytes отображаются как 1`() {
        val raw = byteArrayOf(0, 0, 0, 1, 2)
        val encoded = Base58.encode(raw)
        assertTrue(encoded.startsWith("111"), "три ведущих нуля = три '1', got $encoded")
    }

    @Test
    fun `decode invalid character бросает error`() {
        val ex = runCatching { Base58.decode("ABC0") }.exceptionOrNull()
        assertTrue(ex != null, "0 не в base58 alphabet, должен бросить")
    }
}
