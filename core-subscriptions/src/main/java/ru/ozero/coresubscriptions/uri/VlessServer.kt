package ru.ozero.coresubscriptions.uri

data class VlessServer(
    val uuid: String,
    val host: String,
    val port: Int,
    val encryption: String = "none",
    val security: String = "none",
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val sni: String? = null,
    val transport: String = "tcp",
    val path: String? = null,
    val flow: String? = null,
    val remark: String? = null,
)
