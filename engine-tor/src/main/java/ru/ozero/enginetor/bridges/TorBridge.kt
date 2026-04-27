package ru.ozero.enginetor.bridges

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
            requireSafe(v, "arg value for $k")
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
}

private fun requireSafe(value: String, field: String) {
    require(value.isNotEmpty()) { "$field пустой" }
    require(value.none { it.isWhitespace() || it.code == 0 }) {
        val hex = value.toByteArray().joinToString("") { b -> "%02x".format(b) }
        "$field содержит управляющий символ или пробел: $hex"
    }
}
