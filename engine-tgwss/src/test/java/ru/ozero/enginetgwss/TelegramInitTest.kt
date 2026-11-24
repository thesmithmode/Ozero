package ru.ozero.enginetgwss

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TelegramInitTest {
    @Test
    fun `zero DC is filled without changing the original header`() {
        val original = header(0, 0xef)
        val copy = original.copyOf()
        val patched = TelegramInit.prepare(original, TelegramDestination(2, false))!!
        assertArrayEquals(copy, original)
        assertArrayEquals(header(2, 0xef), patched)
    }

    @Test
    fun `matching media DC is preserved`() {
        val original = header(-4, 0xee)
        assertArrayEquals(original, TelegramInit.prepare(original, TelegramDestination(4, true)))
        assertEquals("kws4-1.web.telegram.org", TelegramDestination(4, true).host)
    }

    @Test
    fun `media hosts and special DC use official endpoint mapping`() {
        assertEquals(
            listOf("kws4-1.web.telegram.org", "kws4.web.telegram.org"),
            TelegramDestination(4, true).hosts,
        )
        assertEquals(listOf("kws2.web.telegram.org"), TelegramDestination(203, false).hosts)
        assertArrayEquals(header(203, 0xdd), TelegramInit.prepare(header(203, 0xdd), TelegramDestination(203, false)))
    }

    @Test
    fun `wrong DC is rejected`() {
        assertNull(TelegramInit.prepare(header(3, 0xef), TelegramDestination(2, false)))
    }

    @Test
    fun `unknown protocol and short header are rejected`() {
        assertNull(TelegramInit.prepare(header(2, 0), TelegramDestination(2, false)))
        assertNull(TelegramInit.prepare(ByteArray(63), TelegramDestination(2, false)))
    }

    private fun header(dc: Int, marker: Int): ByteArray {
        val bytes = ByteArray(64) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(bytes.copyOfRange(8, 40), "AES"),
            IvParameterSpec(bytes.copyOfRange(40, 56)),
        )
        val stream = cipher.doFinal(ByteArray(64))
        for (index in 56..59) bytes[index] = (stream[index].toInt() xor marker).toByte()
        bytes[60] = (stream[60].toInt() xor (dc and 0xff)).toByte()
        bytes[61] = (stream[61].toInt() xor ((dc shr 8) and 0xff)).toByte()
        return bytes
    }
}
