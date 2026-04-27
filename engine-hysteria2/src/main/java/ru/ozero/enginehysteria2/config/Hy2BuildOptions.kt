package ru.ozero.enginehysteria2.config

data class Hy2BuildOptions(
    val socksPort: Int,
    val hopIntervalSeconds: Int = 30,
    val portRange: IntRange? = null,
    val bandwidthUp: String? = null,
    val bandwidthDown: String? = null,
    val pinSHA256: String? = null,
)
