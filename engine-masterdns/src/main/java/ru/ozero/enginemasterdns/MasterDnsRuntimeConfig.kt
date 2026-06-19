package ru.ozero.enginemasterdns

data class MasterDnsRuntimeConfig(
    val configToml: String,
    val resolvers: List<String>,
    val socksPort: Int,
)
