package ru.ozero.enginenaive

interface LibNaiveDelegate {
    fun startNaive(configJson: String): Int
    fun stopNaive(): Int
    fun isAlive(): Boolean
    fun version(): String
}
