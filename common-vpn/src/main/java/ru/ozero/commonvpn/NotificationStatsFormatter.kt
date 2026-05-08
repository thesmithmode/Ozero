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
        val baseLine = "↓ $rxRate  ↑ $txRate"
        val totalLine = "Σ ↓$rxTotal / ↑$txTotal  $duration"
        return if (extras.isNotEmpty()) "$baseLine\n$totalLine · $extras" else "$baseLine\n$totalLine"
    }
}
