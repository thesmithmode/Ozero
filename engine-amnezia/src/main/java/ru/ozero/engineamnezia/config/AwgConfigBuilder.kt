package ru.ozero.engineamnezia.config

import ru.ozero.coresubscriptions.uri.AmneziaWgServer

/**
 * Сборщик INI-конфига AmneziaWG 2.0 (wg-quick формат + поля обфускации).
 *
 * Структура:
 * ```
 * [Interface]
 * PrivateKey = ...
 * Address    = 10.0.0.2/32
 * DNS        = 1.1.1.1, 8.8.8.8
 * MTU        = 1280
 * Jc         = 4
 * Jmin       = 40
 * Jmax       = 70
 * S1         = 0
 * S2         = 0
 * H1         = 0xAABBCCDD
 * H2         = 0x11223344
 * H3         = 99
 * H4         = 100
 *
 * [Peer]
 * PublicKey           = ...
 * PresharedKey        = ...
 * AllowedIPs          = 0.0.0.0/0, ::/0
 * Endpoint            = host:port
 * PersistentKeepalive = 25
 * ```
 *
 * Валидация:
 * - PrivateKey / PublicKey не пустые
 * - Jmin <= Jmax
 * - Если хотя бы один из H1..H4 ≠ 0 — все четыре уникальны и не равны нулю
 *   (требование протокола AmneziaWG: type-байты handshake должны различаться)
 */
class AwgConfigBuilder {

    fun build(server: AmneziaWgServer): String {
        require(server.privateKey.isNotBlank()) { "PrivateKey пустой" }
        require(server.publicKey.isNotBlank()) { "PublicKey пустой" }
        require(server.jmin <= server.jmax) { "Jmin (${server.jmin}) > Jmax (${server.jmax})" }

        // INI-injection guard: подписочный URI URL-декодируется (%0a → \n) — без проверки
        // attacker может встроить произвольные INI-директивы (DNS/AllowedIPs) через
        // строковые поля. Fail-closed: если любое поле содержит \r/\n — отказ.
        fun assertNoCrlf(value: String, name: String) {
            require('\n' !in value && '\r' !in value) { "$name содержит CR/LF (INI-injection?)" }
        }
        assertNoCrlf(server.privateKey, "PrivateKey")
        assertNoCrlf(server.publicKey, "PublicKey")
        server.presharedKey?.let { assertNoCrlf(it, "PresharedKey") }
        assertNoCrlf(server.host, "Endpoint host")
        server.addresses.forEach { assertNoCrlf(it, "Address") }
        server.dns.forEach { assertNoCrlf(it, "DNS") }
        server.allowedIps.forEach { assertNoCrlf(it, "AllowedIPs") }

        val anyH = server.h1 != 0L || server.h2 != 0L || server.h3 != 0L || server.h4 != 0L
        if (anyH) {
            val all = listOf(server.h1, server.h2, server.h3, server.h4)
            require(all.all { it != 0L }) { "H1..H4 заданы частично — нужны все четыре" }
            require(all.toSet().size == 4) { "H1..H4 должны быть уникальны: $all" }
        }

        val sb = StringBuilder()
        sb.appendLine("[Interface]")
        sb.appendLine("PrivateKey = ${server.privateKey}")
        if (server.addresses.isNotEmpty()) {
            sb.appendLine("Address = ${server.addresses.joinToString(", ")}")
        }
        if (server.dns.isNotEmpty()) {
            sb.appendLine("DNS = ${server.dns.joinToString(", ")}")
        }
        sb.appendLine("MTU = ${server.mtu}")

        if (server.jc > 0) sb.appendLine("Jc = ${server.jc}")
        if (server.jmin > 0 || server.jmax > 0) {
            sb.appendLine("Jmin = ${server.jmin}")
            sb.appendLine("Jmax = ${server.jmax}")
        }
        if (server.s1 > 0) sb.appendLine("S1 = ${server.s1}")
        if (server.s2 > 0) sb.appendLine("S2 = ${server.s2}")
        if (anyH) {
            sb.appendLine("H1 = ${server.h1}")
            sb.appendLine("H2 = ${server.h2}")
            sb.appendLine("H3 = ${server.h3}")
            sb.appendLine("H4 = ${server.h4}")
        }

        sb.appendLine()
        sb.appendLine("[Peer]")
        sb.appendLine("PublicKey = ${server.publicKey}")
        if (!server.presharedKey.isNullOrBlank()) {
            sb.appendLine("PresharedKey = ${server.presharedKey}")
        }
        sb.appendLine("AllowedIPs = ${server.allowedIps.joinToString(", ")}")
        sb.appendLine("Endpoint = ${server.host}:${server.port}")
        if (server.persistentKeepalive > 0) {
            sb.appendLine("PersistentKeepalive = ${server.persistentKeepalive}")
        }
        return sb.toString()
    }
}
