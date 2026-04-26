package ru.ozero.coresubscriptions

import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.uri.ParsedServer
import java.security.MessageDigest

class ServerMapper {

    fun toEntity(parsed: ParsedServer, originalUri: String): ServerEntity? =
        when (parsed) {
            is ParsedServer.Vless ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "vless",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.Hysteria2 ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "hysteria2",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.Trojan ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "trojan",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.Shadowsocks ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "shadowsocks",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.AmneziaWg ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "amneziawg",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.Naive ->
                ServerEntity(
                    id = stableId(originalUri),
                    country = countryFromRemark(parsed.server.remark) ?: UNKNOWN_COUNTRY,
                    role = "single",
                    protocol = "naive",
                    uri = originalUri,
                    port = parsed.server.port,
                )

            is ParsedServer.Error -> null
        }

    private fun stableId(uri: String): String {
        // 16 байт = 128 бит → birthday-collision ~1% при 2^57 серверов (астрономически).
        // Раньше 8 байт = 64 бит → ~1% при ~6e8 (по-прежнему много, но запас на будущее).
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return digest.take(STABLE_ID_BYTES).joinToString("") { "%02x".format(it) }
    }

    private fun countryFromRemark(remark: String?): String? {
        if (remark.isNullOrBlank()) return null
        // Heuristic: remark часто "RU-entry-01" → берём первые 2 символа до "-"
        val first = remark.substringBefore("-").takeIf { it.length == 2 } ?: return null
        return first.uppercase()
    }

    private companion object {
        const val UNKNOWN_COUNTRY = "XX"
        const val STABLE_ID_BYTES = 16
    }
}
