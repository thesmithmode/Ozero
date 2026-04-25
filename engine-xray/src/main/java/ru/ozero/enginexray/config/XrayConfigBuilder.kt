package ru.ozero.enginexray.config

import ru.ozero.commonjson.JsonWriter
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import ru.ozero.coresubscriptions.uri.ShadowsocksServer
import ru.ozero.coresubscriptions.uri.TrojanServer
import ru.ozero.coresubscriptions.uri.VlessServer

/**
 * Генератор Xray JSON-конфигов для поддерживаемых протоколов.
 *
 * Ограничения V1:
 *  - Один outbound + один SOCKS inbound на localhost.
 *  - Порядок ключей детерминирован (LinkedHashMap) — тесты сверяют exact-match.
 *  - Не валидирует криптографические поля сервера (это делает SubscriptionFilter).
 *
 * См. docs/backend.md и SPEC §4 для соответствия приоритетам StrategyEngine.
 */
class XrayConfigBuilder {

    fun build(server: VlessServer, socksPort: Int): String {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(server.host.isNotBlank()) { "host пуст" }
        require(server.uuid.isNotBlank()) { "uuid пуст" }

        val root = linkedMapOf<String, Any?>(
            "log" to log(),
            "inbounds" to listOf(socksInbound(socksPort)),
            "outbounds" to listOf(vlessOutbound(server)),
        )
        return JsonWriter.write(root)
    }

    fun build(server: Hysteria2Server, socksPort: Int): String {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(server.host.isNotBlank()) { "host пуст" }
        require(server.password.isNotBlank()) { "password пуст" }

        val root = linkedMapOf<String, Any?>(
            "log" to log(),
            "inbounds" to listOf(socksInbound(socksPort)),
            "outbounds" to listOf(hysteria2Outbound(server)),
        )
        return JsonWriter.write(root)
    }

    fun build(server: TrojanServer, socksPort: Int): String {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(server.host.isNotBlank()) { "host пуст" }
        require(server.password.isNotBlank()) { "password пуст" }

        val root = linkedMapOf<String, Any?>(
            "log" to log(),
            "inbounds" to listOf(socksInbound(socksPort)),
            "outbounds" to listOf(trojanOutbound(server)),
        )
        return JsonWriter.write(root)
    }

    /**
     * Double-hop: entry outbound (RU) → exit outbound (foreign) → internet.
     *
     * Trafic flow: client → SOCKS inbound → entry outbound → entry server → exit outbound
     *              (proxySettings.tag) → exit server → internet.
     *
     * Преимущество: ни entry-server, ни ISP не видят destination,
     * exit-server не видит исходный IP клиента (видит entry-server). Защита от
     * deanonymization: даже если entry-сервер скомпрометирован, exit-сервер не
     * раскрывает реального клиента.
     */
    fun buildChain(entry: VlessServer, exit: VlessServer, socksPort: Int): String {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(entry.host.isNotBlank()) { "entry.host пуст" }
        require(exit.host.isNotBlank()) { "exit.host пуст" }
        require(entry.host != exit.host || entry.port != exit.port) {
            "entry и exit указывают на один сервер — chain бессмыслен"
        }

        val entryOutbound = vlessOutbound(entry).toMutableMap().apply {
            this["tag"] = TAG_ENTRY
            this["proxySettings"] = linkedMapOf<String, Any?>("tag" to TAG_EXIT)
        }
        val exitOutbound = vlessOutbound(exit).toMutableMap().apply {
            this["tag"] = TAG_EXIT
        }

        val root = linkedMapOf<String, Any?>(
            "log" to log(),
            "inbounds" to listOf(socksInbound(socksPort)),
            // Порядок outbound значим для tag-routing: entry должен быть default.
            "outbounds" to listOf(entryOutbound, exitOutbound),
        )
        return JsonWriter.write(root)
    }

    fun build(server: ShadowsocksServer, socksPort: Int): String {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        require(server.host.isNotBlank()) { "host пуст" }
        require(server.method.isNotBlank()) { "method пуст" }

        val root = linkedMapOf<String, Any?>(
            "log" to log(),
            "inbounds" to listOf(socksInbound(socksPort)),
            "outbounds" to listOf(shadowsocksOutbound(server)),
        )
        return JsonWriter.write(root)
    }

    // ---- outbounds ------------------------------------------------------

    private fun vlessOutbound(s: VlessServer): Map<String, Any?> {
        val user = linkedMapOf<String, Any?>(
            "id" to s.uuid,
            "encryption" to s.encryption,
        )
        if (!s.flow.isNullOrBlank()) user["flow"] = s.flow

        val settings = linkedMapOf<String, Any?>(
            "vnext" to listOf(
                linkedMapOf<String, Any?>(
                    "address" to s.host,
                    "port" to s.port,
                    "users" to listOf(user),
                ),
            ),
        )
        return linkedMapOf(
            "tag" to "proxy",
            "protocol" to "vless",
            "settings" to settings,
            "streamSettings" to vlessStreamSettings(s),
        )
    }

    private fun vlessStreamSettings(s: VlessServer): Map<String, Any?> {
        val network = s.transport.ifBlank { "tcp" }
        val stream = linkedMapOf<String, Any?>(
            "network" to network,
            "security" to s.security,
        )
        when (s.security) {
            "reality" -> {
                stream["realitySettings"] = linkedMapOf<String, Any?>(
                    "show" to false,
                    "fingerprint" to (s.fingerprint ?: "chrome"),
                    "serverName" to (s.sni ?: s.host),
                    "publicKey" to (s.publicKey ?: ""),
                    "shortId" to (s.shortId ?: ""),
                    "spiderX" to "",
                )
            }
            "tls" -> {
                stream["tlsSettings"] = linkedMapOf<String, Any?>(
                    "serverName" to (s.sni ?: s.host),
                    "fingerprint" to (s.fingerprint ?: "chrome"),
                    "allowInsecure" to false,
                )
            }
        }
        when (network) {
            "xhttp" -> {
                stream["xhttpSettings"] = linkedMapOf<String, Any?>(
                    "path" to (s.path ?: "/"),
                    "mode" to "auto",
                )
            }
            "grpc" -> {
                stream["grpcSettings"] = linkedMapOf<String, Any?>(
                    "serviceName" to (s.path ?: ""),
                    "multiMode" to false,
                )
            }
            "ws" -> {
                stream["wsSettings"] = linkedMapOf<String, Any?>(
                    "path" to (s.path ?: "/"),
                )
            }
            // tcp — без доп. settings
        }
        return stream
    }

    private fun hysteria2Outbound(s: Hysteria2Server): Map<String, Any?> {
        val server = linkedMapOf<String, Any?>(
            "address" to s.host,
            "port" to s.port,
            "password" to s.password,
        )
        if (!s.obfs.isNullOrBlank()) {
            server["obfs"] = linkedMapOf<String, Any?>(
                "type" to s.obfs,
                "password" to (s.obfsPassword ?: ""),
            )
        }
        val stream = linkedMapOf<String, Any?>(
            "network" to "udp",
            "security" to "tls",
            "tlsSettings" to linkedMapOf<String, Any?>(
                "serverName" to (s.sni ?: s.host),
                "allowInsecure" to s.insecure,
            ),
        )
        return linkedMapOf(
            "tag" to "proxy",
            "protocol" to "hysteria2",
            "settings" to linkedMapOf<String, Any?>("servers" to listOf(server)),
            "streamSettings" to stream,
        )
    }

    private fun trojanOutbound(s: TrojanServer): Map<String, Any?> {
        val server = linkedMapOf<String, Any?>(
            "address" to s.host,
            "port" to s.port,
            "password" to s.password,
        )
        val stream = linkedMapOf<String, Any?>(
            "network" to "tcp",
            "security" to "tls",
            "tlsSettings" to linkedMapOf<String, Any?>(
                "serverName" to (s.sni ?: s.peer ?: s.host),
                "allowInsecure" to false,
            ),
        )
        return linkedMapOf(
            "tag" to "proxy",
            "protocol" to "trojan",
            "settings" to linkedMapOf<String, Any?>("servers" to listOf(server)),
            "streamSettings" to stream,
        )
    }

    private fun shadowsocksOutbound(s: ShadowsocksServer): Map<String, Any?> {
        val server = linkedMapOf<String, Any?>(
            "address" to s.host,
            "port" to s.port,
            "method" to s.method,
            "password" to s.password,
        )
        return linkedMapOf(
            "tag" to "proxy",
            "protocol" to "shadowsocks",
            "settings" to linkedMapOf<String, Any?>("servers" to listOf(server)),
        )
    }

    // ---- shared ---------------------------------------------------------

    private fun log() = linkedMapOf<String, Any?>("loglevel" to "warning")

    private fun socksInbound(port: Int) = linkedMapOf<String, Any?>(
        "tag" to "socks-in",
        "port" to port,
        "listen" to "127.0.0.1",
        "protocol" to "socks",
        "settings" to linkedMapOf<String, Any?>(
            "auth" to "noauth",
            "udp" to true,
        ),
    )

    private companion object {
        const val TAG_ENTRY = "proxy"
        const val TAG_EXIT = "exit-proxy"
    }
}
