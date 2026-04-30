package ru.ozero.commonvpn

data class TunnelStats(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
    val timestampMs: Long,
)
