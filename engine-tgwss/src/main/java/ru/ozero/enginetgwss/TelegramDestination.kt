package ru.ozero.enginetgwss

internal data class TelegramDestination(val dc: Int, val media: Boolean) {
    val host: String
        get() = if (media) "kws$dc-1.web.telegram.org" else "kws$dc.web.telegram.org"

    companion object {
        private val destinations = mapOf(
            "149.154.175.50" to TelegramDestination(1, false),
            "149.154.175.51" to TelegramDestination(1, false),
            "149.154.175.53" to TelegramDestination(1, false),
            "149.154.175.54" to TelegramDestination(1, false),
            "149.154.175.52" to TelegramDestination(1, true),
            "149.154.167.41" to TelegramDestination(2, false),
            "149.154.167.50" to TelegramDestination(2, false),
            "149.154.167.51" to TelegramDestination(2, false),
            "149.154.167.220" to TelegramDestination(2, false),
            "149.154.167.151" to TelegramDestination(2, true),
            "149.154.167.222" to TelegramDestination(2, true),
            "149.154.167.223" to TelegramDestination(2, true),
            "149.154.162.123" to TelegramDestination(2, true),
            "95.161.76.100" to TelegramDestination(2, false),
            "149.154.175.100" to TelegramDestination(3, false),
            "149.154.175.101" to TelegramDestination(3, false),
            "149.154.175.102" to TelegramDestination(3, true),
            "149.154.167.91" to TelegramDestination(4, false),
            "149.154.167.92" to TelegramDestination(4, false),
            "149.154.164.250" to TelegramDestination(4, true),
            "149.154.166.120" to TelegramDestination(4, true),
            "149.154.166.121" to TelegramDestination(4, true),
            "149.154.167.118" to TelegramDestination(4, true),
            "149.154.165.111" to TelegramDestination(4, true),
            "91.108.56.100" to TelegramDestination(5, false),
            "91.108.56.101" to TelegramDestination(5, false),
            "91.108.56.116" to TelegramDestination(5, false),
            "91.108.56.126" to TelegramDestination(5, false),
            "149.154.171.5" to TelegramDestination(5, false),
            "91.108.56.102" to TelegramDestination(5, true),
            "91.108.56.128" to TelegramDestination(5, true),
            "91.108.56.151" to TelegramDestination(5, true),
        )

        fun resolve(ip: String, port: Int): TelegramDestination? =
            if (port in setOf(80, 443, 5222)) destinations[ip] else null
    }
}
