package ru.ozero.coresubscriptions

import ru.ozero.coresubscriptions.uri.ParsedServer

class SubscriptionFilter {

    fun isLiveIn2026(parsed: ParsedServer): Boolean =
        when (parsed) {
            is ParsedServer.Vless -> parsed.server.security == "reality"
            is ParsedServer.Hysteria2 -> true
            is ParsedServer.Trojan -> true
            is ParsedServer.Shadowsocks -> false
                                    is ParsedServer.AmneziaWg -> parsed.server.hasObfuscation()
            is ParsedServer.Naive -> true
            is ParsedServer.Error -> false
        }

    private fun ru.ozero.coresubscriptions.uri.AmneziaWgServer.hasObfuscation(): Boolean =
        jc > 0 ||
            jmin > 0 ||
            jmax > 0 ||
            s1 > 0 ||
            s2 > 0 ||
            h1 != 0L ||
            h2 != 0L ||
            h3 != 0L ||
            h4 != 0L
}
