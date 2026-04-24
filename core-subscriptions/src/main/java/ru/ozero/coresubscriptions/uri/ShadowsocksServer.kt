package ru.ozero.coresubscriptions.uri

data class ShadowsocksServer(
    val method: String,
    val password: String,
    val host: String,
    val port: Int,
    val remark: String? = null,
)
