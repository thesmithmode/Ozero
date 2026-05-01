package ru.ozero.engineurnetwork.auth

interface UrnetworkAuthService {
    suspend fun acquireGuestJwt(): GuestJwtResult
}

sealed class GuestJwtResult {
    data class Success(val byJwt: String) : GuestJwtResult()
    data class Error(val message: String) : GuestJwtResult()
}
