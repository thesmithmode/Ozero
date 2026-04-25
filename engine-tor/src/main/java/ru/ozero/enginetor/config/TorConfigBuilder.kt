package ru.ozero.enginetor.config

import ru.ozero.enginetor.bridges.TorBridge

/**
 * Опции сборки torrc.
 *
 * @param ptBinaries абсолютные пути к PT-бинарям (`obfs4` → "/path/obfs4proxy" и т.д.).
 *                   Tor вызывает их при handshake; путь должен быть исполняемым.
 * @param dataDir директория для тор-данных (DataDirectory).
 * @param socksPort локальный SOCKS5 listener (default 9050).
 */
data class TorBuildOptions(
    val socksPort: Int = 9050,
    val controlPort: Int = 9051,
    val dataDir: String = "/data/local/tmp/tor",
    val ptBinaries: Map<String, String> = emptyMap(),
    val excludeExitNodes: List<String> = listOf("{ru}", "{by}"),
)

/**
 * Сборщик torrc — конфиг tor 0.4.x с поддержкой PT bridges.
 *
 * Структура:
 * ```
 * SocksPort 9050
 * ControlPort 9051
 * DataDirectory /path
 * UseBridges 1
 * ClientTransportPlugin obfs4 exec /path/obfs4proxy
 * ClientTransportPlugin snowflake exec /path/snowflake-client
 * Bridge obfs4 IP:PORT FP cert=... iat-mode=0
 * Bridge snowflake host:port FP url=... front=...
 * ExcludeExitNodes {ru},{by}
 * StrictNodes 1
 * ```
 */
class TorConfigBuilder {

    fun build(bridges: List<TorBridge>, options: TorBuildOptions = TorBuildOptions()): String {
        require(options.socksPort in MIN_PORT..MAX_PORT) { "socksPort вне диапазона" }
        require(options.controlPort in MIN_PORT..MAX_PORT) { "controlPort вне диапазона" }
        require(options.socksPort != options.controlPort) { "socksPort == controlPort" }

        val sb = StringBuilder()
        sb.appendLine("SocksPort ${options.socksPort}")
        sb.appendLine("ControlPort ${options.controlPort}")
        sb.appendLine("DataDirectory ${options.dataDir}")

        if (bridges.isNotEmpty()) {
            sb.appendLine("UseBridges 1")
            // ClientTransportPlugin строка на каждый уникальный transport
            val transports = bridges.map { it.transport }.toSet()
            for (t in transports.sorted()) {
                val bin = options.ptBinaries[t]
                require(!bin.isNullOrBlank()) { "ptBinaries[$t] не задан — нужен путь к PT-бинарю" }
                sb.appendLine("ClientTransportPlugin $t exec $bin")
            }
            for (b in bridges) sb.appendLine(b.toTorrcLine())
        }

        if (options.excludeExitNodes.isNotEmpty()) {
            sb.appendLine("ExcludeExitNodes ${options.excludeExitNodes.joinToString(",")}")
            sb.appendLine("StrictNodes 1")
        }

        return sb.toString()
    }

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65535
    }
}
