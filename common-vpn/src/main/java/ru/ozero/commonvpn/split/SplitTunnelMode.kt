package ru.ozero.commonvpn.split

/**
 * Режим split-tunnel.
 *
 * - [ALL]        — весь трафик идёт через VPN (default).
 * - [BYPASS_LAN] — VPN, кроме локальных подсетей (RFC1918 + link-local + loopback).
 *                  Пользовательский Wi-Fi router/NAS/принтеры доступны напрямую.
 * - [ALLOWLIST]  — только указанные пакеты идут через VPN; остальные — напрямую.
 * - [BLOCKLIST]  — все, кроме указанных пакетов; типично для "не пускать банк-приложение в VPN".
 */
enum class SplitTunnelMode { ALL, BYPASS_LAN, ALLOWLIST, BLOCKLIST }

data class SplitTunnelConfig(
    val mode: SplitTunnelMode = SplitTunnelMode.ALL,
    val packages: Set<String> = emptySet(),
)
