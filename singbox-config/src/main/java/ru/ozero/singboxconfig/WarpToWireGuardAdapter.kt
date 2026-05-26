package ru.ozero.singboxconfig

import ru.ozero.enginescore.WireGuardOutboundConfig

object WarpToWireGuardAdapter {

    fun convert(
        privateKey: String,
        peerPublicKey: String,
        peerEndpoint: String,
        interfaceAddressV4: String,
        interfaceAddressV6: String,
        mtu: Int,
        keepaliveSeconds: Int,
    ): WireGuardOutboundConfig {
        val (host, port) = splitEndpoint(peerEndpoint)
        val addresses = buildList {
            if (interfaceAddressV4.isNotEmpty()) {
                add(if ('/' in interfaceAddressV4) interfaceAddressV4 else "$interfaceAddressV4/32")
            }
            if (interfaceAddressV6.isNotEmpty()) {
                add(if ('/' in interfaceAddressV6) interfaceAddressV6 else "$interfaceAddressV6/128")
            }
        }
        return WireGuardOutboundConfig(
            privateKey = privateKey,
            peerPublicKey = peerPublicKey,
            serverHost = host,
            serverPort = port,
            localAddresses = addresses,
            mtu = mtu.coerceIn(MIN_MTU, MAX_MTU),
            keepaliveSeconds = keepaliveSeconds,
        )
    }

    internal fun splitEndpoint(endpoint: String): Pair<String, Int> {
        val bracketClose = endpoint.lastIndexOf(']')
        val colonIdx = if (bracketClose >= 0) {
            endpoint.indexOf(':', bracketClose + 1)
        } else {
            endpoint.lastIndexOf(':')
        }
        require(colonIdx > 0) { "invalid endpoint: $endpoint" }
        val host = endpoint.substring(0, colonIdx).removeSurrounding("[", "]")
        val port = endpoint.substring(colonIdx + 1).toInt()
        return host to port
    }

    private const val MIN_MTU = 1280
    private const val MAX_MTU = 1500
}
