package ru.ozero.commonvpn

import java.util.Locale
import kotlin.math.abs

object BytesFormatter {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
    private const val UNIT_STEP = 1024.0

    fun humanReadable(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val absBytes = abs(bytes).toDouble()
        if (absBytes < UNIT_STEP) return "$bytes B"
        var value = absBytes
        var unit = 0
        while (value >= UNIT_STEP && unit < UNITS.size - 1) {
            value /= UNIT_STEP
            unit++
        }
        val signed = if (bytes < 0) -value else value
        return String.format(Locale.US, "%.1f %s", signed, UNITS[unit])
    }
}
