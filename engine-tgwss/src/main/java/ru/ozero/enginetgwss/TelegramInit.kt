package ru.ozero.enginetgwss

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object TelegramInit {
    const val SIZE = 64

    fun prepare(header: ByteArray, destination: TelegramDestination): ByteArray? {
        if (header.size != SIZE) return null
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(header.copyOfRange(8, 40), "AES"),
            IvParameterSpec(header.copyOfRange(40, 56)),
        )
        val stream = cipher.doFinal(ByteArray(SIZE))
        val plain = ByteArray(8) { index ->
            (header[56 + index].toInt() xor stream[56 + index].toInt()).toByte()
        }
        val marker = plain[0].toInt() and 0xff
        if (marker !in setOf(0xef, 0xee, 0xdd)) return null
        if (plain.take(4).any { (it.toInt() and 0xff) != marker }) return null
        val current = ((plain[4].toInt() and 0xff) or ((plain[5].toInt() and 0xff) shl 8)).toShort().toInt()
        val expected = if (destination.media) -destination.dc else destination.dc
        if (current != 0 && current != expected) return null
        return header.copyOf().apply {
            this[60] = (stream[60].toInt() xor (expected and 0xff)).toByte()
            this[61] = (stream[61].toInt() xor ((expected shr 8) and 0xff)).toByte()
        }
    }
}
