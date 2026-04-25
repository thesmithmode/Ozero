package ru.ozero.enginehysteria2.porthop

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Port-hopping для Hysteria2: детерминированный для пары (authKey, epoch/interval) и
 * непредсказуемый для DPI без authKey. Используется HMAC-SHA256 — клиент и сервер,
 * зная общий [authKey], получают одинаковый порт в каждом окне длиной [intervalSeconds].
 *
 * Алгоритм:
 *   slot = epochSeconds / intervalSeconds
 *   raw  = HMAC-SHA256(authKey, BE(slot))[0..3]  // первые 4 байта
 *   port = (raw mod (range.last - range.first + 1)) + range.first
 */
class PortHopper(
    private val authKey: String,
    private val range: IntRange,
    private val intervalSeconds: Int,
) {

    init {
        require(authKey.isNotBlank()) { "authKey пустой" }
        require(intervalSeconds > 0) { "intervalSeconds должен быть > 0" }
        require(range.first <= range.last) { "range пустой/инвертирован" }
        require(range.first in MIN_PORT..MAX_PORT && range.last in MIN_PORT..MAX_PORT) {
            "range вне 1..65535: $range"
        }
    }

    private val mac: Mac = Mac.getInstance(HMAC_ALG).apply {
        init(SecretKeySpec(authKey.toByteArray(Charsets.UTF_8), HMAC_ALG))
    }

    private val rangeSize = (range.last - range.first + 1).toLong()

    fun portAt(epochSeconds: Long): Int {
        val slot = epochSeconds / intervalSeconds
        val raw = synchronized(mac) {
            mac.reset()
            mac.doFinal(longBe(slot))
        }
        val u32 = ((raw[0].toLong() and 0xFF) shl 24) or
            ((raw[1].toLong() and 0xFF) shl 16) or
            ((raw[2].toLong() and 0xFF) shl 8) or
            (raw[3].toLong() and 0xFF)
        return (u32 % rangeSize).toInt() + range.first
    }

    /** Время (epoch sec) ближайшей будущей границы окна. */
    fun nextHopAt(epochSeconds: Long): Long {
        val nextSlot = epochSeconds / intervalSeconds + 1
        return nextSlot * intervalSeconds
    }

    private fun longBe(v: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) out[7 - i] = ((v ushr (i * 8)) and 0xFF).toByte()
        return out
    }

    private companion object {
        const val HMAC_ALG = "HmacSHA256"
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}
