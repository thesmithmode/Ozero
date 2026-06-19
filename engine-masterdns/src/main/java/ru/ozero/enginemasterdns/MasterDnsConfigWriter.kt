package ru.ozero.enginemasterdns

import java.io.File

open class MasterDnsConfigWriter(private val workDir: File) {

    data class Files(val configPath: String, val resolversPath: String)

    open fun write(runtime: MasterDnsRuntimeConfig): Files {
        workDir.mkdirs()
        val configFile = File(workDir, "client_config.toml")
        val resolversFile = File(workDir, "client_resolvers.txt")
        configFile.writeText(buildToml(runtime))
        val resolversText = if (runtime.resolvers.isEmpty()) {
            ""
        } else {
            runtime.resolvers.joinToString(separator = "\n", postfix = "\n")
        }
        resolversFile.writeText(resolversText)
        return Files(configFile.absolutePath, resolversFile.absolutePath)
    }

    private fun buildToml(runtime: MasterDnsRuntimeConfig): String {
        val rewritten = runtime.configToml
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                OVERRIDE_KEYS.any { trimmed.startsWith(it, ignoreCase = true) }
            }
            .joinToString("\n")
            .trimEnd()
        return buildString {
            append(rewritten)
            append("\n\n")
            append("LISTEN_IP = \"127.0.0.1\"\n")
            append("LISTEN_PORT = ").append(runtime.socksPort).append("\n")
            append("LOCAL_DNS_ENABLED = false\n")
        }
    }

    private companion object {
        val OVERRIDE_KEYS = listOf(
            "LISTEN_IP",
            "LISTEN_PORT",
            "LOCAL_DNS_ENABLED",
            "OZERO_READINESS_HOST",
            "OZERO_READINESS_PORT",
            "OZERO_READINESS_TIMEOUT_MS",
            "OZERO_READINESS_POLL_INTERVAL_MS",
            "OZERO_READINESS_CONNECT_TIMEOUT_MS",
        )
    }
}
