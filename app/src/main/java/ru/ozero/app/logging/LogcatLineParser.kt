package ru.ozero.app.logging

import java.util.Calendar
import java.util.regex.Pattern

/**
 * Парсер одной строки `logcat -v threadtime` в [LogEntry].
 *
 * Формат threadtime:
 * `MM-DD HH:MM:SS.sss  PID  TID LEVEL TAG: message`
 * Пример: `04-27 10:32:18.421  1234  5678 I OzeroVpn: startVpn`
 *
 * Год logcat не выводит — подставляем текущий, плюс защита от переноса через
 * новый год: если месяц строки больше текущего на >6, считаем что это прошлый
 * год (актуально только при просмотре буфера через 1 января).
 */
object LogcatLineParser {

    private val PATTERN: Pattern = Pattern.compile(
        "^(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+" +
            "(\\d+)\\s+\\d+\\s+([VDIWEFA])\\s+([^:]+?):\\s?(.*)$",
    )

    fun parse(line: String, nowYear: Int = Calendar.getInstance().get(Calendar.YEAR)): LogEntry? {
        val m = PATTERN.matcher(line)
        if (!m.matches()) return null
        val month = m.group(1)!!.toInt()
        val day = m.group(2)!!.toInt()
        val hour = m.group(3)!!.toInt()
        val minute = m.group(4)!!.toInt()
        val second = m.group(5)!!.toInt()
        val millis = m.group(6)!!.toInt()
        val pid = m.group(7)!!.toInt()
        val level = LogLevel.fromShort(m.group(8)!![0])
        val tag = m.group(9)!!.trim()
        val message = m.group(10) ?: ""

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val year = if (month - currentMonth > 6) nowYear - 1 else nowYear
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, millis)

        return LogEntry(
            timestampMs = cal.timeInMillis,
            level = level,
            tag = tag,
            pid = pid,
            message = message,
        )
    }
}
