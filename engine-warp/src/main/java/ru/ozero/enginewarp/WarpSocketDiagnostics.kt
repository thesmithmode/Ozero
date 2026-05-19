package ru.ozero.enginewarp

import java.io.File

internal object WarpSocketDiagnostics {

    fun listSocketCandidates(uapiPath: String): String =
        runCatching {
            val root = File(uapiPath)
            val rootList = root.listFiles()?.joinToString(",") { it.name } ?: "null"
            val wg = File(root, "wireguard")
            val wgList = if (wg.exists()) {
                wg.listFiles()?.joinToString(",") { it.name } ?: "empty"
            } else {
                "absent"
            }
            "[$uapiPath]={$rootList}; [wireguard/]={$wgList}"
        }.getOrElse { "dirListing failed: ${it.message}" }
}
