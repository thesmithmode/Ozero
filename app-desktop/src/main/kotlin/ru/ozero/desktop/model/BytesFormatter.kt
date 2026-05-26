package ru.ozero.desktop.model

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

    fun humanReadablePerSec(bytesPerSec: Double): String {
        if (bytesPerSec <= 0.0) return "0 B/s"
        if (bytesPerSec < UNIT_STEP) return String.format(Locale.US, "%.0f B/s", bytesPerSec)
        var value = bytesPerSec
        var unit = 0
        while (value >= UNIT_STEP && unit < UNITS.size - 1) {
            value /= UNIT_STEP
            unit++
        }
        return String.format(Locale.US, "%.1f %s/s", value, UNITS[unit])
    }

    fun durationHms(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
