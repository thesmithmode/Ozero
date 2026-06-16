package ru.ozero.enginewarp

import java.nio.file.Paths

internal object WarpSocketDiagnostics {

    fun listSocketCandidates(uapiPath: String): String =
        runCatching {
            val root = Paths.get(uapiPath).toFile()
            val rootList = root.listFiles()?.joinToString(",") { it.name } ?: "null"
            val sockets = root.toPath().resolve("sockets").toFile()
            val socketsList = if (sockets.exists()) {
                sockets.listFiles()?.joinToString(",") { it.name } ?: "empty"
            } else {
                "absent"
            }
            val wg = root.toPath().resolve("wireguard").toFile()
            val wgList = if (wg.exists()) {
                wg.listFiles()?.joinToString(",") { it.name } ?: "empty"
            } else {
                "absent"
            }
            "[$uapiPath]={$rootList}; [sockets/]={$socketsList}; [wireguard/]={$wgList}"
        }.getOrElse { e -> "dirListing failed: ${e.message}" }
}
