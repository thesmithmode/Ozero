package ru.ozero.coresubscriptions.uri

data class NaiveServer(
    val scheme: String,
    val username: String,
    val password: String,
    val host: String,
    val port: Int,
    val remark: String? = null,
) {
    val proxyUrl: String
        get() = "$scheme://$username:$password@$host:$port"

    val isQuic: Boolean
        get() = scheme.equals("quic", ignoreCase = true) ||
            scheme.equals("https3", ignoreCase = true)
}
