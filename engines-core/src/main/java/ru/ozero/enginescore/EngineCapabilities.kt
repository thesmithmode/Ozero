package ru.ozero.enginescore

data class EngineCapabilities(
    val supportsTcp: Boolean,
    val supportsUdp: Boolean,
    val supportsDoH: Boolean,
    val localOnly: Boolean,
    val requiresServer: Boolean,
    val supportsUpstreamSocks: Boolean,
    val providesLocalSocks: Boolean = true,
    val providesLocalSocksWithoutUpstream: Boolean = providesLocalSocks,
)
