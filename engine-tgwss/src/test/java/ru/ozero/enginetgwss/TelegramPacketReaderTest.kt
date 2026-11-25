package ru.ozero.enginetgwss

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TelegramPacketReaderTest {
    @Test
    fun `abridged packets preserve boundaries and encryption`() {
        val short = byteArrayOf(0x82.toByte()) + ByteArray(8) { 3 }
        val long = byteArrayOf(0xff.toByte(), 127, 0, 0) + ByteArray(508) { 4 }
        val (reader, encrypted) = reader(0xef, short + long)
        assertArrayEquals(encrypted.copyOfRange(0, short.size), reader.readPacket())
        assertArrayEquals(encrypted.copyOfRange(short.size, encrypted.size), reader.readPacket())
        assertNull(reader.readPacket())
    }

    @Test
    fun `intermediate quick ack flag is masked`() {
        val (reader, encrypted) = reader(0xee, byteArrayOf(4, 0, 0, 0x80.toByte()) + ByteArray(4))
        assertArrayEquals(encrypted, reader.readPacket())
        assertNull(reader.readPacket())
    }

    @Test
    fun `padded intermediate accepts non aligned payload`() {
        val (reader, encrypted) = reader(0xdd, byteArrayOf(7, 0, 0, 0) + ByteArray(7))
        assertArrayEquals(encrypted, reader.readPacket())
    }

    @Test
    fun `zero and oversized packet lengths are rejected`() {
        assertThrows(IOException::class.java) { reader(0xef, byteArrayOf(0)).first.readPacket() }
        assertThrows(IOException::class.java) { reader(0xaa, byteArrayOf(1)).first.readPacket() }
        assertThrows(IOException::class.java) {
            reader(0xee, byteArrayOf(1, 0, 64, 0)).first.readPacket()
        }
    }

    @Test
    fun `truncated headers and payloads are rejected`() {
        assertThrows(EOFException::class.java) { reader(0xef, byteArrayOf(127, 1)).first.readPacket() }
        assertThrows(EOFException::class.java) { reader(0xdd, byteArrayOf(4, 0, 0, 0, 1)).first.readPacket() }
    }

    private fun reader(marker: Int, plain: ByteArray): Pair<TelegramPacketReader, ByteArray> {
        val header = ByteArray(64) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(header.copyOfRange(8, 40), "AES"),
            IvParameterSpec(header.copyOfRange(40, 56)),
        )
        val stream = cipher.update(ByteArray(64))
        for (index in 56..59) header[index] = (stream[index].toInt() xor marker).toByte()
        val encrypted = cipher.update(plain)
        val reader = TelegramPacketReader(DataInputStream(ByteArrayInputStream(encrypted)), header)
        return reader to encrypted
    }
}
