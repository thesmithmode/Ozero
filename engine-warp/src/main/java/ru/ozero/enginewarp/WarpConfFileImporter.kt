package ru.ozero.enginewarp

import java.io.IOException
import java.io.InputStream

class WarpConfFileImporter : WarpFileImporter {
    override fun import(stream: InputStream): Result<ImportedWarpConfig> = try {
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (text.isBlank()) {
            Result.failure(IOException("Файл пустой"))
        } else {
            WarpConfParser.parse(text).map { config ->
                ImportedWarpConfig(config = config, rawIni = text)
            }
        }
    } catch (t: Throwable) {
        Result.failure(IOException("Ошибка чтения файла: ${t.message}", t))
    }
}
