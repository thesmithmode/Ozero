package ru.ozero.enginescore

sealed class Upstream {
    data object None : Upstream()
    data class Socks5(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : Upstream()
    data class Http(val host: String, val port: Int) : Upstream()
}
