package ru.ozero.app.urnetwork

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface UrnetworkSharedTrafficHistory {
    fun loadLast30Days(today: LocalDate = LocalDate.now()): List<DayBytes>
    fun record(cumulativeUnpaidBytes: Long, today: LocalDate = LocalDate.now())
    fun clear()
}

data class DayBytes(val date: LocalDate, val bytes: Long)

class RealUrnetworkSharedTrafficHistory(context: Context) : UrnetworkSharedTrafficHistory {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadLast30Days(today: LocalDate): List<DayBytes> {
        val map = readMap()
        return (29 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            DayBytes(d, map[d.format(DATE_FORMAT)] ?: 0L)
        }
    }

    override fun record(cumulativeUnpaidBytes: Long, today: LocalDate) {
        val map = readMap().toMutableMap()
        val lastCumulative = prefs.getLong(KEY_LAST_CUMULATIVE, -1L)
        val delta = when {
            lastCumulative < 0L -> 0L
            cumulativeUnpaidBytes < lastCumulative -> cumulativeUnpaidBytes.coerceAtLeast(0L)
            else -> cumulativeUnpaidBytes - lastCumulative
        }
        if (delta > 0L) {
            val key = today.format(DATE_FORMAT)
            map[key] = (map[key] ?: 0L) + delta
        }
        pruneOlderThan30Days(map, today)
        prefs.edit()
            .putLong(KEY_LAST_CUMULATIVE, cumulativeUnpaidBytes)
            .putString(KEY_DAILY, JSONObject(map.mapValues { it.value }).toString())
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readMap(): Map<String, Long> {
        val raw = prefs.getString(KEY_DAILY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val out = HashMap<String, Long>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optLong(k, 0L)
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun pruneOlderThan30Days(map: MutableMap<String, Long>, today: LocalDate) {
        val cutoff = today.minusDays(MAX_DAYS_RETENTION.toLong())
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val d = runCatching { LocalDate.parse(entry.key, DATE_FORMAT) }.getOrNull() ?: continue
            if (d.isBefore(cutoff)) iter.remove()
        }
    }

    private companion object {
        const val PREFS_NAME = "urnetwork_shared_traffic_history"
        const val KEY_DAILY = "daily_v1"
        const val KEY_LAST_CUMULATIVE = "last_cumulative_v1"
        const val MAX_DAYS_RETENTION = 35
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
