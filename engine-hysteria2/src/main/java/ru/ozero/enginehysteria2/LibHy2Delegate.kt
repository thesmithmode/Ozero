package ru.ozero.enginehysteria2

interface LibHy2Delegate {
    fun startHy2(configJson: String): Int
    fun stopHy2(): Int
    fun version(): String
    fun queryStats(direction: String): Long
}
