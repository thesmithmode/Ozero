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
    init {
        requireSafe(transport, "transport")
        requireSafe(address, "address")
        requireSafe(fingerprint, "fingerprint")
        for ((k, v) in args) {
            requireSafe(k, "arg key")
            requireSafe(v, "arg[$k]")
        }
    }

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

    private companion object {
        private val UNSAFE = Regex("[\\r\\n\\t\\u0000 ]")

        fun requireSafe(value: String, field: String) {
            require(value.isNotEmpty()) { "$field пустой" }
            require(!UNSAFE.containsMatchIn(value)) {
                "$field содержит управляющий символ или пробел: ${value.toByteArray().toHexString()}"
            }
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
    }
}
