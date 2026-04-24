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
            is ParsedServer.Error -> false
        }
}
