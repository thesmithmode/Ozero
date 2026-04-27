package ru.ozero.enginetor.config

import ru.ozero.enginetor.bridges.TorBridge

data class TorBuildOptions(
    val socksPort: Int = 9050,
    val controlPort: Int = 9051,
    val dataDir: String,
    val ptBinaries: Map<String, String> = emptyMap(),
    val excludeExitNodes: List<String> = listOf("{ru}", "{by}"),
)

class TorConfigBuilder {

    fun build(bridges: List<TorBridge>, options: TorBuildOptions): String {
        require(options.socksPort in MIN_PORT..MAX_PORT) { "socksPort вне диапазона" }
        require(options.controlPort in MIN_PORT..MAX_PORT) { "controlPort вне диапазона" }
        require(options.socksPort != options.controlPort) { "socksPort == controlPort" }
        require(options.dataDir.isNotBlank()) { "dataDir пуст — задайте context.filesDir/tor" }
        require(!options.dataDir.contains(Regex("[\\n\\r]"))) { "dataDir содержит CR/LF (torrc injection)" }
        for (node in options.excludeExitNodes) {
            require(!node.contains(Regex("[\\n\\r,]"))) { "excludeExitNode '$node' содержит CR/LF/comma" }
        }

        val sb = StringBuilder()
        sb.appendLine("SocksPort 127.0.0.1:${options.socksPort}")
        sb.appendLine("ControlPort 127.0.0.1:${options.controlPort}")
        sb.appendLine("DataDirectory ${options.dataDir}")

        if (bridges.isNotEmpty()) {
            sb.appendLine("UseBridges 1")
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
