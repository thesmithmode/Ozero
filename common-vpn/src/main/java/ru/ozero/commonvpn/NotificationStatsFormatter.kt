package ru.ozero.commonvpn

object NotificationStatsFormatter {

    fun format(snapshot: TunnelStats, extras: String, nowMs: Long = System.currentTimeMillis()): String {
        val rxRate = BytesFormatter.humanReadablePerSec(snapshot.bpsIn)
        val txRate = BytesFormatter.humanReadablePerSec(snapshot.bpsOut)
        val rxTotal = BytesFormatter.humanReadable(snapshot.rxBytes)
        val txTotal = BytesFormatter.humanReadable(snapshot.txBytes)
        val durationMs = if (snapshot.sessionStartMs > 0L) {
            (nowMs - snapshot.sessionStartMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val duration = BytesFormatter.durationHms(durationMs)
        val speedLine = if (extras.isNotEmpty()) "$extras  ↓ $rxRate  ↑ $txRate" else "↓ $rxRate  ↑ $txRate"
        val totalLine = "↓ $rxTotal / ↑ $txTotal  $duration"
        return "$speedLine\n$totalLine"
    }
}
