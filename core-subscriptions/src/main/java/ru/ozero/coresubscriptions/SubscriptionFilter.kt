package ru.ozero.coresubscriptions

import ru.ozero.coresubscriptions.uri.ParsedServer

/**
 * Фильтрует протоколы по SPEC §6.3 "isLiveIn2026".
 *
 * Мёртвые (резаны ТСПУ/РКН на апрель 2026): OpenVPN, WG, L2TP, PPTP,
 * VLESS без Reality, AmneziaWG 1.x, Shadowsocks без обёртки, VMess.
 * Живые: VLESS+Reality (XHTTP/gRPC), Hysteria2, AmneziaWG 2.0, NaiveProxy,
 * Trojan+ShadowTLS, ByeDPI локально.
 */
class SubscriptionFilter {

    fun isLiveIn2026(parsed: ParsedServer): Boolean =
        when (parsed) {
            is ParsedServer.Vless -> parsed.server.security == "reality"
            is ParsedServer.Hysteria2 -> true
            is ParsedServer.Trojan -> true
            is ParsedServer.Shadowsocks -> false
            // AmneziaWG 2.0 — только если задан хоть один параметр обфускации,
            // иначе это голый WG, который ТСПУ режет на handshake.
            is ParsedServer.AmneziaWg -> parsed.server.hasObfuscation()
            is ParsedServer.Error -> false
        }

    private fun ru.ozero.coresubscriptions.uri.AmneziaWgServer.hasObfuscation(): Boolean =
        jc > 0 || jmin > 0 || jmax > 0 || s1 > 0 || s2 > 0 ||
            h1 != 0L || h2 != 0L || h3 != 0L || h4 != 0L
}
