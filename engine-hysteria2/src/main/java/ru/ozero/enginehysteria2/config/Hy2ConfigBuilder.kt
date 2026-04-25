package ru.ozero.enginehysteria2.config

import ru.ozero.commonjson.JsonWriter
import ru.ozero.coresubscriptions.uri.Hysteria2Server

/**
 * Сборщик клиентского JSON-конфига для apernet/hysteria v2.
 * Schema: server, auth, tls{sni,insecure,pinSHA256?}, obfs?{type,salamander{password}},
 * bandwidth?{up,down}, socks5{listen}, transport{type=udp, udp{hopInterval}}.
 *
 * Порядок ключей фиксирован (LinkedHashMap) — exact-match тесты + детерминированный hash конфига.
 */
class Hy2ConfigBuilder {

    fun build(server: Hysteria2Server, options: Hy2BuildOptions): String {
        require(options.socksPort in MIN_PORT..MAX_PORT) {
            "socksPort вне диапазона: ${options.socksPort}"
        }

        val serverAddress = if (options.portRange != null) {
            "${server.host}:${options.portRange.first}-${options.portRange.last}"
        } else {
            "${server.host}:${server.port}"
        }

        val root = linkedMapOf<String, Any?>(
            "server" to serverAddress,
            "auth" to server.password,
            "tls" to tlsBlock(server, options),
        )

        obfsBlock(server)?.let { root["obfs"] = it }
        bandwidthBlock(options)?.let { root["bandwidth"] = it }

        root["socks5"] = linkedMapOf<String, Any?>(
            "listen" to "127.0.0.1:${options.socksPort}",
        )

        root["transport"] = linkedMapOf<String, Any?>(
            "type" to "udp",
            "udp" to linkedMapOf<String, Any?>(
                "hopInterval" to "${options.hopIntervalSeconds}s",
            ),
        )

        return JsonWriter.write(root)
    }

    private fun tlsBlock(server: Hysteria2Server, options: Hy2BuildOptions): Map<String, Any?> {
        val tls = linkedMapOf<String, Any?>()
        if (!server.sni.isNullOrBlank()) tls["sni"] = server.sni
        tls["insecure"] = server.insecure
        if (!options.pinSHA256.isNullOrBlank()) tls["pinSHA256"] = options.pinSHA256
        return tls
    }

    private fun obfsBlock(server: Hysteria2Server): Map<String, Any?>? {
        val pwd = server.obfsPassword
        if (pwd.isNullOrBlank()) return null
        val type = server.obfs?.takeIf { it.isNotBlank() } ?: "salamander"
        require(type in SUPPORTED_OBFS) { "неизвестный obfs type: $type (поддерживается ${SUPPORTED_OBFS.joinToString()})" }
        return linkedMapOf<String, Any?>(
            "type" to type,
            type to linkedMapOf<String, Any?>("password" to pwd),
        )
    }

    private fun bandwidthBlock(options: Hy2BuildOptions): Map<String, Any?>? {
        if (options.bandwidthUp.isNullOrBlank() && options.bandwidthDown.isNullOrBlank()) return null
        val bw = linkedMapOf<String, Any?>()
        options.bandwidthUp?.let { bw["up"] = it }
        options.bandwidthDown?.let { bw["down"] = it }
        return bw
    }

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
        val SUPPORTED_OBFS = setOf("salamander")
    }
}
