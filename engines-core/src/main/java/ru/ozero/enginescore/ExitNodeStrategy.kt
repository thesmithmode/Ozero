package ru.ozero.enginescore

sealed class ExitNodeStrategy {
    data object DirectHttp : ExitNodeStrategy()
    data class ViaSocks(val host: String, val port: Int) : ExitNodeStrategy()
    data class LocationOnly(val country: String?, val countryCode: String?) : ExitNodeStrategy()
    data class ProviderLabel(
        val label: String,
        val ip: String? = null,
        val countryCode: String? = null,
    ) : ExitNodeStrategy()
    data class AutoSelected(val label: String = "Авто") : ExitNodeStrategy()
    data class Unavailable(val reason: String) : ExitNodeStrategy()
}
