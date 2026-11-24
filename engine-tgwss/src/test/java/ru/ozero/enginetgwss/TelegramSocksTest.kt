package ru.ozero.enginetgwss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException

class TelegramSocksTest {
    @Test
    fun `known Telegram DC is accepted`() {
        val (destination, response) = negotiate(5, 1, 0, 5, 1, 0, 1, 149, 154, 167, 41, 1, 187)
        assertEquals(TelegramDestination(2, false), destination)
        assertEquals(0, response[3].toInt())
    }

    @Test
    fun `non Telegram address is rejected`() {
        val (destination, response) = negotiate(5, 1, 0, 5, 1, 0, 1, 127, 0, 0, 1, 1, 187)
        assertNull(destination)
        assertEquals(2, response[3].toInt())
    }

    @Test
    fun `unsupported method is rejected`() {
        val (destination, response) = negotiate(5, 1, 2)
        assertNull(destination)
        assertEquals(255, response[1].toInt() and 0xff)
    }

    @Test
    fun `UDP is rejected`() {
        val (destination, response) = negotiate(5, 1, 0, 5, 3, 0, 1)
        assertNull(destination)
        assertEquals(7, response[3].toInt())
    }

    @Test
    fun `domain requests are rejected without DNS`() {
        val (destination, response) = negotiate(5, 1, 0, 5, 1, 0, 3)
        assertNull(destination)
        assertEquals(8, response[3].toInt())
    }

    @Test
    fun `truncated request is rejected`() {
        assertThrows(EOFException::class.java) { negotiate(5, 1, 0, 5) }
    }

    @Test
    fun `unsupported port is rejected`() {
        assertNull(negotiate(5, 1, 0, 5, 1, 0, 1, 149, 154, 167, 41, 0, 22).first)
    }

    private fun negotiate(vararg data: Int): Pair<TelegramDestination?, ByteArray> {
        val input = DataInputStream(ByteArrayInputStream(data.map(Int::toByte).toByteArray()))
        val output = ByteArrayOutputStream()
        return TelegramSocks.negotiate(input, output) to output.toByteArray()
    }
}
