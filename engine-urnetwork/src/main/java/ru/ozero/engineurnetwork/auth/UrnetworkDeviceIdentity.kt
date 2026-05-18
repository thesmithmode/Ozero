package ru.ozero.engineurnetwork.auth

interface UrnetworkDeviceIdentity {
    suspend fun pubkeyBase58(): String
    suspend fun sign(message: ByteArray): ByteArray
}
