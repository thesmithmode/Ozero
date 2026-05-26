package ru.ozero.desktop.vpn

import org.json.JSONArray
import org.json.JSONObject

class SingboxConfigBuilder {

    private var socksPort: Int = 0
    private var tunAddress: String = "172.19.0.1/30"
    private var tunMtu: Int = 9000
    private var dnsServer: String = "8.8.8.8"
    private var directProcessNames: List<String> = emptyList()
    private var proxyOnlyProcessNames: List<String> = emptyList()

    fun socksUpstream(port: Int) = apply { socksPort = port }
    fun tunAddress(addr: String) = apply { tunAddress = addr }
    fun tunMtu(mtu: Int) = apply { tunMtu = mtu }
    fun dnsServer(server: String) = apply { dnsServer = server }
    fun bypassProcesses(names: List<String>) = apply { directProcessNames = names }
    fun proxyOnlyProcesses(names: List<String>) = apply { proxyOnlyProcessNames = names }

    fun buildTun2Socks(): String {
        require(socksPort in 1..65535) { "socksPort out of range: $socksPort" }
        val config = JSONObject()

        config.put("dns", buildDns())
        config.put("inbounds", buildTunInbound())
        config.put("outbounds", buildOutbounds())
        config.put("route", buildRoute())

        return config.toString(2)
    }

    private fun buildDns(): JSONObject = JSONObject().apply {
        put("servers", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "remote-dns")
                put("type", "udp")
                put("server", dnsServer)
            })
        })
    }

    private fun buildTunInbound(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "ozero-tun")
            put("address", JSONArray().put(tunAddress))
            put("mtu", tunMtu)
            put("auto_route", true)
            put("strict_route", true)
        })
    }

    private fun buildOutbounds(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "socks")
            put("tag", "proxy")
            put("server", "127.0.0.1")
            put("server_port", socksPort)
        })
        put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })
    }

    private fun buildRoute(): JSONObject = JSONObject().apply {
        val rules = JSONArray()

        rules.put(JSONObject().put("action", "sniff"))

        rules.put(JSONObject().apply {
            put("protocol", "dns")
            put("action", "hijack-dns")
        })

        rules.put(JSONObject().apply {
            put("ip_is_private", true)
            put("outbound", "direct")
        })

        if (directProcessNames.isNotEmpty()) {
            rules.put(JSONObject().apply {
                put("process_name", JSONArray(directProcessNames))
                put("outbound", "direct")
            })
        }

        if (proxyOnlyProcessNames.isNotEmpty()) {
            rules.put(JSONObject().apply {
                put("process_name", JSONArray(proxyOnlyProcessNames))
                put("outbound", "proxy")
            })
            rules.put(JSONObject().apply {
                put("outbound", "direct")
            })
        }

        put("rules", rules)
        put("auto_detect_interface", true)
        put("default_domain_resolver", "remote-dns")
    }
}
