package ru.ozero.enginetelegram

data class TelegramProxyConfig(
    val enabled: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val domain: String = DEFAULT_DOMAIN,
    val secret: String = "",
) {
    companion object {
        const val DEFAULT_PORT = 3128
        const val DEFAULT_DOMAIN = "www.google.com"
    }
}
