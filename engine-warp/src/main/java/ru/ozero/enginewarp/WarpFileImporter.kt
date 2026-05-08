package ru.ozero.enginewarp

import java.io.InputStream

data class ImportedWarpConfig(
    val config: WarpConfig,
    val rawIni: String,
)

interface WarpFileImporter {
    fun import(stream: InputStream): Result<ImportedWarpConfig>
}
