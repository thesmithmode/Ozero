package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationStatsFormatterTest {

    private val baseSnapshot = TunnelStats(
        txPackets = 0,
        txBytes = 2_500_000,
        rxPackets = 0,
        rxBytes = 12_500_000,
        timestampMs = 100_000,
        bpsIn = 1_500_000.0,
        bpsOut = 320_000.0,
        sessionStartMs = 0L,
    )

    @Test
    fun format_содержит_speed_total_duration() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(sessionStartMs = 0L),
            extras = "",
            nowMs = 100_000,
        )
        assertTrue(result.contains("MB/s") || result.contains("KB/s"), "должен содержать единицу скорости: $result")
        assertTrue(result.contains("MB"), "должен содержать total bytes: $result")
        assertTrue(result.contains("00:00") || result.contains(":"), "должен содержать длительность: $result")
    }

    @Test
    fun format_двухстрочный_когда_extras_пуст() {
        val result = NotificationStatsFormatter.format(baseSnapshot, "", 100_000)
        assertEquals(2, result.lines().size, "ровно две строки без extras: $result")
    }

    @Test
    fun format_содержит_extras_когда_не_пусто() {
        val result = NotificationStatsFormatter.format(baseSnapshot, "5 peers", 100_000)
        assertTrue(result.contains("5 peers"), "extras должен попасть в текст: $result")
    }

    @Test
    fun format_extras_не_появляется_если_пустая_строка() {
        val result = NotificationStatsFormatter.format(baseSnapshot, "", 100_000)
        assertFalse(result.contains("·"), "разделитель · должен быть только при наличии extras: $result")
    }

    @Test
    fun format_длительность_растёт_от_sessionStartMs() {
        val zero = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(sessionStartMs = 100_000),
            extras = "",
            nowMs = 100_000,
        )
        val tenSec = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(sessionStartMs = 100_000),
            extras = "",
            nowMs = 110_000,
        )
        assertTrue(zero.contains("00:00"), "00:00 при nowMs == sessionStartMs: $zero")
        assertTrue(tenSec.contains("00:10"), "00:10 при +10s: $tenSec")
    }

    @Test
    fun format_negative_duration_clamped_to_zero() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(sessionStartMs = 200_000),
            extras = "",
            nowMs = 100_000,
        )
        assertTrue(result.contains("00:00"), "отрицательная длительность → 00:00: $result")
    }

    @Test
    fun format_zero_speed_не_крашится() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(bpsIn = 0.0, bpsOut = 0.0),
            extras = "",
            nowMs = 100_000,
        )
        assertTrue(result.contains("0 B/s"), "нулевая скорость → '0 B/s': $result")
    }

    @Test
    fun format_zero_total_bytes() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(rxBytes = 0L, txBytes = 0L),
            extras = "",
            nowMs = 100_000,
        )
        assertTrue(result.contains("0 B"), "нулевой total → '0 B': $result")
    }

    @Test
    fun format_извлекает_скорость_из_bpsIn_и_bpsOut() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot.copy(bpsIn = 1_500_000.0, bpsOut = 320_000.0),
            extras = "",
            nowMs = 100_000,
        )
        assertTrue(result.contains("1.4 MB/s"), "rate in 1.5MB/s shown: $result")
    }

    @Test
    fun format_показывает_total_отдельно_от_rate() {
        val result = NotificationStatsFormatter.format(
            snapshot = baseSnapshot,
            extras = "",
            nowMs = 100_000,
        )
        val totalLine = result.lines().last()
        assertTrue(totalLine.contains("↓") && totalLine.contains("/") && totalLine.contains("↑"),
            "вторая строка содержит rx/tx total через /: $totalLine")
    }

    @Test
    fun format_extras_появляется_в_первой_строке() {
        val result = NotificationStatsFormatter.format(baseSnapshot, "Sing-box", 100_000)
        val speedLine = result.lines().first()
        assertTrue(speedLine.startsWith("Sing-box"), "extras в начале первой строки: $speedLine")
        assertTrue(speedLine.contains("↓") && speedLine.contains("↑"), "первая строка содержит скорости: $speedLine")
    }
}
