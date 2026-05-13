package ru.ozero.commonnet

data class NetworkProfile(
    val id: String,
    val transport: Transport,
    val label: String? = null,
) {
    enum class Transport { WIFI, MOBILE, ETHERNET, VPN, OTHER, NONE }

    companion object {
        const val NONE_ID: String = "none"
        val NONE: NetworkProfile = NetworkProfile(id = NONE_ID, transport = Transport.NONE, label = null)
    }
}

interface NetworkProfileDetector {
    fun current(): NetworkProfile
}
