package ru.ozero.enginetgwss

import java.io.DataInputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class TelegramPacketReader(
    private val input: DataInputStream,
    header: ByteArray,
) {
    private val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(header.copyOfRange(8, 40), "AES"),
            IvParameterSpec(header.copyOfRange(40, 56)),
        )
    }
    private val marker = cipher.update(header)[56].toInt() and 0xff

    fun readPacket(): ByteArray? {
        val first = input.read()
        if (first < 0) return null
        val prefix = byteArrayOf(first.toByte())
        val firstPlain = cipher.update(prefix)[0].toInt() and 0xff
        val lengthHeader = when (marker) {
            0xef -> if ((firstPlain and 0x7f) == 0x7f) 4 else 1
            0xee, 0xdd -> 4
            else -> throw IOException("Unsupported MTProto transport")
        }
        val tail = ByteArray(lengthHeader - 1).also { input.readFully(it) }
        val plain = if (tail.isEmpty()) ByteArray(0) else cipher.update(tail)
        val length = when (marker) {
            0xef -> if (lengthHeader == 1) {
                (firstPlain and 0x7f) * 4
            } else {
                ((plain[0].toInt() and 0xff) or
                    ((plain[1].toInt() and 0xff) shl 8) or
                    ((plain[2].toInt() and 0xff) shl 16)) * 4
            }
            else -> firstPlain or
                ((plain[0].toInt() and 0xff) shl 8) or
                ((plain[1].toInt() and 0xff) shl 16) or
                ((plain[2].toInt() and 0x7f) shl 24)
        }
        if (length !in 1..MAX_PAYLOAD) throw IOException("Invalid MTProto packet length")
        val payload = ByteArray(length).also { input.readFully(it) }
        cipher.update(payload)
        return prefix + tail + payload
    }

    private companion object {
        const val MAX_PAYLOAD = 4 * 1024 * 1024
    }
}
