package ru.ozero.enginewarp

import java.io.IOException
import java.io.InputStream

class WarpConfFileImporter {
    fun import(stream: InputStream): Result<WarpConfig> = try {
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (text.isBlank()) {
            Result.failure(IOException("Файл пустой"))
        } else {
            WarpConfParser.parse(text)
        }
    } catch (t: Throwable) {
        Result.failure(IOException("Ошибка чтения файла: ${t.message}", t))
    }
}
