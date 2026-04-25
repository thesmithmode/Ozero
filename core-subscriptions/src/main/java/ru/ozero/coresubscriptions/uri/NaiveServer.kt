package ru.ozero.coresubscriptions.uri

/**
 * NaiveProxy — HTTP/2 (или QUIC) CONNECT через Chromium net stack.
 * Fingerprint = реальный Chrome → DPI неотличим от обычного HTTPS-трафика.
 *
 * URI: `naive+https://user:pass@host:port#remark`  (HTTP/2)
 *      `naive+quic://user:pass@host:port#remark`   (QUIC)
 */
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
