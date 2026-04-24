package ru.ozero.coresubscriptions.uri

data class Hysteria2Server(
    val password: String,
    val host: String,
    val port: Int,
    val sni: String? = null,
    val insecure: Boolean = false,
    val obfs: String? = null,
    val obfsPassword: String? = null,
    val remark: String? = null,
)
