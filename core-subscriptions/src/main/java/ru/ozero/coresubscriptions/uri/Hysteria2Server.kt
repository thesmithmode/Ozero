package ru.ozero.coresubscriptions.uri

data class Hysteria2Server(
    val password: String,
    val host: String,
    val port: Int,
    val sni: String? = null,
    val sniAlternatives: List<String> = emptyList(),
    val insecure: Boolean = false,
    val obfs: String? = null,
    val obfsPassword: String? = null,
    val pinSHA256: String? = null,
    val portRangeStart: Int? = null,
    val portRangeEnd: Int? = null,
    val bandwidthUp: String? = null,
    val bandwidthDown: String? = null,
    val remark: String? = null,
)
