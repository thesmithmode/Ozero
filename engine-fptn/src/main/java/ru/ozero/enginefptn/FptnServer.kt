package ru.ozero.enginefptn

data class FptnServer(
    val name: String,
    val host: String,
    val port: Int,
    val md5Fingerprint: String,
    val countryCode: String?,
)
