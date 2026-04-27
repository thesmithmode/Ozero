package ru.ozero.engineamnezia

interface LibAwgDelegate {
    fun startAwg(configIni: String): Int
    fun stopAwg(): Int
    fun isUp(): Boolean
    fun version(): String
    fun queryStats(direction: String): Long
}
