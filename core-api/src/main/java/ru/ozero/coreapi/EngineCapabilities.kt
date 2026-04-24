package ru.ozero.coreapi

data class EngineCapabilities(
    val supportsTcp: Boolean,
    val supportsUdp: Boolean,
    val supportsDoH: Boolean,
    val localOnly: Boolean,
    val requiresServer: Boolean
)
