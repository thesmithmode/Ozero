package ru.ozero.app.urnetwork

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrnetworkSharedTrafficHistoryTest {

    private fun history(prefs: InMemorySharedPreferences = InMemorySharedPreferences()) =
        RealUrnetworkSharedTrafficHistory(mockUrnetworkContext(prefs))

    @Test
    fun `loadLast30Days returns full 30 day window and zeros for missing days`() {
        val today = LocalDate.of(2026, 6, 7)
        val prefs = InMemorySharedPreferences().apply {
            putRaw(
                "daily_v1",
                """
                {
                  "2026-06-07": 15,
                  "2026-06-06": 10
                }
                """.trimIndent(),
            )
        }

        val loaded = history(prefs).loadLast30Days(today)

        assertEquals(30, loaded.size)
        assertEquals(10L, loaded.first { it.date == today.minusDays(1) }.bytes)
        assertEquals(15L, loaded.last { it.date == today }.bytes)
        assertEquals(0L, loaded.first().bytes)
    }

    @Test
    fun `record first cumulative sample stores baseline without day delta`() {
        val today = LocalDate.of(2026, 6, 7)
        val prefs = InMemorySharedPreferences()
        val history = history(prefs)

        history.record(100L, today)

        assertEquals(100L, prefs.getLong("last_cumulative_v1", -1L))
        assertTrue(history.loadLast30Days(today).all { it.bytes == 0L })
    }

    @Test
    fun `record positive delta accumulates onto existing day`() {
        val today = LocalDate.of(2026, 6, 7)
        val prefs = InMemorySharedPreferences().apply {
            putRaw(
                "daily_v1",
                """
                {
                  "2026-06-07": 25
                }
                """.trimIndent(),
            )
            edit().putLong("last_cumulative_v1", 100L).apply()
        }
        val history = history(prefs)

        history.record(175L, today)

        val todayBytes = history.loadLast30Days(today).first { it.date == today }.bytes
        assertEquals(100L, todayBytes)
        assertEquals(175L, prefs.getLong("last_cumulative_v1", -1L))
    }

    @Test
    fun `record uses current cumulative when it drops and prunes stale dates while keeping invalid keys`() {
        val today = LocalDate.of(2026, 6, 7)
        val prefs = InMemorySharedPreferences().apply {
            putRaw(
                "daily_v1",
                """
                {
                  "2026-04-20": 999,
                  "2026-05-10": 7,
                  "not-a-date": 11
                }
                """.trimIndent(),
            )
            edit().putLong("last_cumulative_v1", 500L).apply()
        }
        val history = history(prefs)

        history.record(120L, today)

        val loaded = history.loadLast30Days(today)
        assertEquals(120L, loaded.first { it.date == today }.bytes)
        assertEquals(7L, loaded.first { it.date == today.minusDays(28) }.bytes)
        assertTrue(loaded.none { it.date == today.minusDays(48) })
        val rawDaily = prefs.rawString("daily_v1")
        assertNotNull(rawDaily)
        assertTrue(rawDaily.contains("not-a-date"))
        assertFalse(rawDaily.contains("2026-04-20"))
    }

    @Test
    fun `loadLast30Days returns zeros when stored JSON is malformed`() {
        val today = LocalDate.of(2026, 6, 7)
        val prefs = InMemorySharedPreferences().apply {
            putRaw("daily_v1", "{broken")
        }

        val loaded = history(prefs).loadLast30Days(today)

        assertEquals(30, loaded.size)
        assertTrue(loaded.all { it.bytes == 0L })
    }

    @Test
    fun `clear removes all stored history`() {
        val prefs = InMemorySharedPreferences().apply {
            putRaw("daily_v1", """{"2026-06-07": 1}""")
            putRaw("last_cumulative_v1", "1")
        }
        val history = history(prefs)

        history.clear()

        assertNull(prefs.rawString("daily_v1"))
        assertFalse(prefs.contains("last_cumulative_v1"))
    }
}
