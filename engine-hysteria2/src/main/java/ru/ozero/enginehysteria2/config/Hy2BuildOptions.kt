package ru.ozero.enginehysteria2.config

/**
 * Параметры построения Hy2-конфига вне самой подписки.
 *
 * @param socksPort локальный SOCKS5-listener (1..65535)
 * @param hopIntervalSeconds интервал ротации UDP-порта (default 30s)
 * @param portRange диапазон серверных портов для port-hopping (host:start-end)
 * @param bandwidthUp / [bandwidthDown] лимиты в формате Hy2 ("100 mbps")
 * @param pinSHA256 SHA256-пин TLS-сертификата (anti-MITM от РКН)
 */
data class Hy2BuildOptions(
    val socksPort: Int,
    val hopIntervalSeconds: Int = 30,
    val portRange: IntRange? = null,
    val bandwidthUp: String? = null,
    val bandwidthDown: String? = null,
    val pinSHA256: String? = null,
)
