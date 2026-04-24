package ru.ozero.enginexray

import ru.ozero.coresubscriptions.uri.VlessServer

class XrayConfigBuilder {

    fun buildVless(server: VlessServer, socksPort: Int): String {
        val outbound = buildOutbound(server)
        return """
            {
              "log":{"loglevel":"warning"},
              "inbounds":[{"port":$socksPort,"protocol":"socks","settings":{"udp":true,"auth":"noauth"},"sniffing":{"enabled":true,"destOverride":["http","tls"]}}],
              "outbounds":[$outbound]
            }
        """.trimIndent().replace("\n", "").replace(Regex("\\s+(?=[,:{}\\[\\]])"), "")
    }

    private fun buildOutbound(server: VlessServer): String {
        val user = buildUser(server)
        val stream = buildStream(server)
        return """{"protocol":"vless","settings":{"vnext":[{"address":"${esc(server.host)}","port":${server.port},"users":[$user]}]},"streamSettings":$stream}"""
    }

    private fun buildUser(server: VlessServer): String {
        val flow = server.flow?.takeIf { it.isNotEmpty() }?.let { ",\"flow\":\"${esc(it)}\"" } ?: ""
        return """{"id":"${esc(server.uuid)}","encryption":"${esc(server.encryption)}"$flow}"""
    }

    private fun buildStream(server: VlessServer): String {
        val reality = if (server.security == "reality") buildRealitySettings(server) else ""
        val suffix = if (reality.isNotEmpty()) ",\"realitySettings\":$reality" else ""
        return """{"network":"${esc(server.transport)}","security":"${esc(server.security)}"$suffix}"""
    }

    private fun buildRealitySettings(server: VlessServer): String =
        """
        {"serverName":"${esc(server.sni.orEmpty())}","fingerprint":"${esc(server.fingerprint.orEmpty())}","publicKey":"${esc(server.publicKey.orEmpty())}","shortId":"${esc(server.shortId.orEmpty())}"}
        """.trimIndent()

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
