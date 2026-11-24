package ru.ozero.engineopera

import ru.ozero.enginescore.TunSpec

object OperaTunConfig {
    private const val MTU = 9000
    private const val V4 = "172.19.0.1"
    private const val V6 = "fdfe:dcba:9876::1"
    private const val V4_DNS = "172.19.0.2"
    private const val V6_DNS = "fdfe:dcba:9876::2"
    private const val V4_PREFIX = 30
    private const val V6_PREFIX = 126

    fun spec(): TunSpec = TunSpec(
        sessionName = "Opera Proxy",
        mtu = MTU,
        blocking = false,
        ipv4Address = V4,
        ipv4PrefixLength = V4_PREFIX,
        dnsServers = listOf(V4_DNS, V6_DNS),
        allowFamilyV6 = true,
        ipv6Address = V6,
        ipv6PrefixLength = V6_PREFIX,
        routeAllV6 = true,
    )

    fun build(port: Int, ipv6: Boolean): String {
        require(port in 1..65535)
        val addresses = if (ipv6) "\"$V4/$V4_PREFIX\",\"$V6/$V6_PREFIX\"" else "\"$V4/$V4_PREFIX\""
        val strategy = if (ipv6) "prefer_ipv4" else "ipv4_only"
        return """
            {
              "log":{"level":"warn"},
              "inbounds":[{"type":"tun","tag":"tun-in","address":[$addresses],
                "mtu":$MTU,"auto_route":false,"strict_route":false}],
              "outbounds":[{"type":"socks","tag":"opera","server":"127.0.0.1","server_port":$port,"version":"5",
                "network":"tcp"}],
              "dns":{"servers":[{"type":"https","tag":"doh","server":"1.1.1.1","server_port":443,
                "path":"/dns-query","tls":{"enabled":true,"server_name":"cloudflare-dns.com"},"detour":"opera"}],
                "final":"doh","strategy":"$strategy"},
              "route":{"auto_detect_interface":true,"final":"opera","rules":[
                {"port":53,"action":"hijack-dns"},
                {"action":"sniff"},
                {"protocol":"dns","action":"hijack-dns"},
                {"network":"udp","action":"reject"}]}
            }
        """.trimIndent()
    }
}
