package ru.ozero.coresubscriptions.uri

data class AmneziaWgServer(
    val privateKey: String,
    val publicKey: String,
    val host: String,
    val port: Int,
    val presharedKey: String? = null,
    val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
    val addresses: List<String> = emptyList(),
    val dns: List<String> = emptyList(),
    val mtu: Int = DEFAULT_MTU,
    val persistentKeepalive: Int = 0,
    val jc: Int = 0,
    val jmin: Int = 0,
    val jmax: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val h1: Long = 0L,
    val h2: Long = 0L,
    val h3: Long = 0L,
    val h4: Long = 0L,
    val remark: String? = null,
) {
    companion object {
        const val DEFAULT_MTU = 1280
    }
}
