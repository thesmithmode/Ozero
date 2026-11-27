package ru.ozero.engineopera

import java.io.File

object OperaProxyCommand {
    const val BINARY_NAME = "libopera_proxy.so"
    const val LOOPBACK = "127.0.0.1"

    fun build(binary: File, config: OperaProxyConfig, port: Int): List<String> {
        require(port in 1..65535)
        return listOf(
            binary.absolutePath,
            "-bind-address", "$LOOPBACK:$port",
            "-socks-mode",
            "-country", config.region.name,
            "-server-selection", config.selection.argument,
            "-server-selection-timeout", "15s",
            "-server-selection-dl-limit", "65536",
            "-timeout", "10s",
            "-init-retries", "1",
            "-verbosity", "60",
        ) + if (config.alternateBootstrap) {
            // The upstream Android wrapper uses encrypted DNS for API discovery.
            // Retain a separate probe target when the default benchmark fails.
            listOf(
                "-bootstrap-dns", "https://1.1.1.1/dns-query,https://8.8.8.8/dns-query,https://dns.quad9.net/dns-query",
                "-server-selection-test-url", "https://example.com/",
            )
        } else {
            emptyList()
        }
    }
}
