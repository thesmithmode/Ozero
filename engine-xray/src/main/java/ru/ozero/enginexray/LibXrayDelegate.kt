package ru.ozero.enginexray

interface LibXrayDelegate {
    fun startXray(configJson: String): Int
    fun stopXray(): Int
    fun version(): String
    fun queryStats(tag: String, direction: String): Long
}
