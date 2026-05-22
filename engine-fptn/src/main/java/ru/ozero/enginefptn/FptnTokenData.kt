package ru.ozero.enginefptn

data class FptnTokenData(
    val version: Int,
    val username: String,
    val password: String,
    val servers: List<FptnServer>,
) {
    override fun toString() = "FptnTokenData(user=***, servers=${servers.map { it.name }})"
}
