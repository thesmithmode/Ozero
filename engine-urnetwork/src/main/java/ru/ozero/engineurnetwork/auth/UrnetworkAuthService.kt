package ru.ozero.engineurnetwork.auth

interface UrnetworkAuthService {
    suspend fun acquireGuestJwt(): GuestJwtResult
    suspend fun acquireClientJwt(byJwt: String): ClientJwtResult
    suspend fun acquireDeviceWalletJwt(
        identity: UrnetworkDeviceIdentity,
        networkName: String,
    ): DeviceWalletJwtResult = DeviceWalletJwtResult.Error("acquireDeviceWalletJwt not implemented")
}

sealed class GuestJwtResult {
    data class Success(val byJwt: String) : GuestJwtResult()
    data class Error(val message: String) : GuestJwtResult()
}

sealed class ClientJwtResult {
    data class Success(val byClientJwt: String) : ClientJwtResult()
    data class Error(val message: String) : ClientJwtResult()
}

sealed class DeviceWalletJwtResult {
    data class Success(val byJwt: String, val isNewNetwork: Boolean) : DeviceWalletJwtResult()
    data class Error(val message: String) : DeviceWalletJwtResult()
}
