package ru.ozero.enginetor

interface LibTorDelegate {
    fun startTor(torrc: String): Int
    fun stopTor(): Int
    fun isBootstrapped(): Boolean
    fun bootstrapPercent(): Int
    fun version(): String
}
