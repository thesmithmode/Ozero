package ru.ozero.enginewarp

import java.io.File

internal object WarpSocketDiagnostics {

    fun listSocketCandidates(uapiPath: String): String =
        runCatching {
            val root = File(uapiPath)
            val rootList = root.listFiles()?.joinToString(",") { it.name } ?: "null"
            val sockets = File(root, "sockets")
            val socketsList = if (sockets.exists()) {
                sockets.listFiles()?.joinToString(",") { it.name } ?: "empty"
            } else {
                "absent"
            }
            val wg = File(root, "wireguard")
            val wgList = if (wg.exists()) {
                wg.listFiles()?.joinToString(",") { it.name } ?: "empty"
            } else {
                "absent"
            }
            "[$uapiPath]={$rootList}; [sockets/]={$socketsList}; [wireguard/]={$wgList}"
        }.getOrElse { "dirListing failed: ${it.message}" }
}
