package ru.ozero.enginescore

sealed class Upstream {
    object None : Upstream()
    data class Socks5(val host: String, val port: Int) : Upstream()
    data class Http(val host: String, val port: Int) : Upstream()
}
