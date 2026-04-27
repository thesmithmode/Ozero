package ru.ozero.engineurnetwork

interface UrnetworkDelegate {
    fun connect(jwtToken: String, apiUrl: String, region: String?, mode: UrnetworkMode): Boolean

    fun disconnect()

    fun connectionStatus(): UrnetworkConnectionStatus

    fun sdkVersion(): String
}

enum class UrnetworkConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

enum class UrnetworkMode {
    CONSUMER,

    PROVIDER,
}
