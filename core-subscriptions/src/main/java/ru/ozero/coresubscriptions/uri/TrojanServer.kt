package ru.ozero.coresubscriptions.uri

data class TrojanServer(
    val password: String,
    val host: String,
    val port: Int,
    val sni: String? = null,
    val peer: String? = null,
    val remark: String? = null,
)
