package ru.ozero.enginemasque

import java.io.File

enum class MasqueTransport {
    AUTO,
    H3,
    H2,
    ;

    internal fun attempts(): List<MasqueTransport> = when (this) {
        AUTO -> listOf(H3, H2)
        H3 -> listOf(H3)
        H2 -> listOf(H2)
    }
}

internal object MasqueCommand {
    fun register(binary: File, config: File): List<String> = listOf(
        binary.absolutePath,
        "--config",
        config.absolutePath,
        "register",
        "--accept-tos",
    )

    fun socks(
        binary: File,
        config: File,
        port: Int,
        transport: MasqueTransport,
        ipv6Enabled: Boolean = false,
    ): List<String> {
        require(port in 1..65535) { "Invalid SOCKS port" }
        require(transport != MasqueTransport.AUTO) { "Select a concrete transport" }
        return buildList {
            addAll(
                listOf(
                    binary.absolutePath,
                    "--config",
                    config.absolutePath,
                    "socks",
                    "--bind",
                    "127.0.0.1",
                    "--port",
                    port.toString(),
                ),
            )
            if (!ipv6Enabled) add("--no-tunnel-ipv6")
            if (transport == MasqueTransport.H2) add("--http2")
        }
    }
}
