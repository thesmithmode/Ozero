package ru.ozero.commondns

object PublicDnsServers {

    val IPV4: List<String> = listOf(
        "8.8.8.8",
        "1.1.1.1",
        "8.8.4.4",
        "1.0.0.1",
        "9.9.9.9",
        "149.112.112.112",
    )

    val IPV6: List<String> = listOf(
        "2001:4860:4860::8888",
        "2606:4700:4700::1111",
        "2001:4860:4860::8844",
        "2606:4700:4700::1001",
    )

    val ALL: List<String> = IPV4 + IPV6

    val DOH_ENDPOINTS: List<String> = listOf(
        "https://dns.google/dns-query",
        "https://cloudflare-dns.com/dns-query",
        "https://dns.quad9.net/dns-query",
    )
}
