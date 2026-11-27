package ru.ozero.enginescore

data class TunSpec(
    val sessionName: String,
    val mtu: Int,
    val blocking: Boolean,
    val ipv4Address: String,
    val ipv4PrefixLength: Int,
    val dnsServers: List<String>,
    val allowFamilyV4: Boolean = true,
    val allowFamilyV6: Boolean = false,
    val ipv6Address: String? = null,
    val ipv6PrefixLength: Int = 0,
    val excludeRfc1918: Boolean = false,
    val routeAllV4: Boolean = true,
    val routeAllV6: Boolean = false,
    val routeCidrsV4: List<String> = emptyList(),
    val routeCidrsV6: List<String> = emptyList(),
)
