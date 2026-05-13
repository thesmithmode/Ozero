package ru.ozero.enginescore

sealed class IpProbeRoute {
    data object Default : IpProbeRoute()
    data object AutoSelected : IpProbeRoute()
    data class Socks(val host: String, val port: Int) : IpProbeRoute()
    data class StaticLocation(val country: String?, val countryCode: String?) : IpProbeRoute()
    data class Unavailable(val reason: String) : IpProbeRoute()
}
