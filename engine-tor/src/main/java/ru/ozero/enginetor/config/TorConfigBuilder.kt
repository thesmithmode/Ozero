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
    // dataDir обязательный — должен указывать на context.filesDir/tor (private app storage).
    // /data/local/tmp world-readable на рутованных устройствах → подмена consensus.
    val dataDir: String,
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

    fun build(bridges: List<TorBridge>, options: TorBuildOptions): String {
        require(options.socksPort in MIN_PORT..MAX_PORT) { "socksPort вне диапазона" }
        require(options.controlPort in MIN_PORT..MAX_PORT) { "controlPort вне диапазона" }
        require(options.socksPort != options.controlPort) { "socksPort == controlPort" }
        require(options.dataDir.isNotBlank()) { "dataDir пуст — задайте context.filesDir/tor" }

        val sb = StringBuilder()
        // SocksPort и ControlPort биндятся на 127.0.0.1, иначе любое приложение в той же сети
        // может отправить AUTHENTICATE+NEWNYM или прочитать circuit info через control protocol.
        sb.appendLine("SocksPort 127.0.0.1:${options.socksPort}")
        sb.appendLine("ControlPort 127.0.0.1:${options.controlPort}")
        sb.appendLine("DataDirectory ${options.dataDir}")

        if (bridges.isNotEmpty()) {
            sb.appendLine("UseBridges 1")
            // ClientTransportPlugin строка на каждый уникальный transport
            val transports = bridges.map { it.transport }.toSet()
            for (t in transports.sorted()) {
                val bin = options.ptBinaries[t]
                require(!bin.isNullOrBlank()) { "ptBinaries[$t] не задан — нужен путь к PT-бинарю" }
                require(!bin.contains(Regex("[\\n\\r]"))) { "ptBinaries[$t] содержит перевод строки (torrc injection)" }
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
