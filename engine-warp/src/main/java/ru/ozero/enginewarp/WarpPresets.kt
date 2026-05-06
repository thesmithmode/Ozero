package ru.ozero.enginewarp

data class EndpointPreset(val id: String, val name: String, val endpoint: String)
data class DnsPreset(val id: String, val name: String, val servers: List<String>)

object EndpointPresets {
    val ALL: List<EndpointPreset> = listOf(
        EndpointPreset("engage_2408", "engage:2408", "engage.cloudflareclient.com:2408"),
        EndpointPreset("engage_4500", "engage:4500", "engage.cloudflareclient.com:4500"),
        EndpointPreset("engage_500", "engage:500", "engage.cloudflareclient.com:500"),
        EndpointPreset("ip_192_2408", "192.1:2408", "162.159.192.1:2408"),
        EndpointPreset("ip_193_2408", "193.1:2408", "162.159.193.1:2408"),
        EndpointPreset("ip_195_2408", "195.1:2408", "162.159.195.1:2408"),
        EndpointPreset("ip_193_4500", "193.10:4500", "162.159.193.10:4500"),
        EndpointPreset("ip_193_1701", "193.10:1701", "162.159.193.10:1701"),
        EndpointPreset("ip_193_500", "193.10:500", "162.159.193.10:500"),
        EndpointPreset("ip_193_854", "193.10:854", "162.159.193.10:854"),
    )
}

object DnsPresets {
    val ALL: List<DnsPreset> = listOf(
        DnsPreset("cf", "Cloudflare", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")),
        DnsPreset("cf_warp", "CF WARP", listOf("162.159.36.1", "162.159.46.1")),
        DnsPreset("google", "Google", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")),
        DnsPreset("yandex", "Yandex", listOf("77.88.8.8", "77.88.8.1")),
        DnsPreset("adguard", "AdGuard", listOf("94.140.14.14", "94.140.15.15")),
        DnsPreset("quad9", "Quad9", listOf("9.9.9.9", "149.112.112.112")),
    )
}
