package ru.ozero.enginetor.bridges

/**
 * Сериализованный bridge для torrc — `Bridge <transport> <addr> <fingerprint> [args...]`.
 *
 * Поддерживаемые транспорты:
 * - `obfs4`        — TLS-обфускация, `cert=` и `iat-mode=` параметры
 * - `snowflake`    — WebRTC через volunteer proxies, опц. `url=`/`front=`
 * - `webtunnel`    — HTTPS-туннель через CDN-fronting
 * - `meek_lite`    — domain fronting через `front=` и `url=`
 * - `conjure`      — refraction networking, набор `args=`
 */
data class TorBridge(
    val transport: String,
    val address: String,
    val fingerprint: String,
    val args: Map<String, String> = emptyMap(),
    val remark: String? = null,
) {
    fun toTorrcLine(): String {
        val sb = StringBuilder("Bridge ")
        sb.append(transport).append(' ')
        sb.append(address).append(' ')
        sb.append(fingerprint)
        for ((k, v) in args) {
            sb.append(' ').append(k).append('=').append(v)
        }
        return sb.toString()
    }
}
