package ru.ozero.commonnet

data class IpInfo(
    val ip: String,
    val country: String?,
    val countryCode: String?,
    val city: String?,
    val fetchedAtMs: Long,
)
