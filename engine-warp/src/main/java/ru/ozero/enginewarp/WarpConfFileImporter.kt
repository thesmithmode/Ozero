package ru.ozero.enginewarp

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class WarpConfFileImporter : WarpFileImporter {
    override fun import(stream: InputStream): Result<ImportedWarpConfig> = try {
        val text = stream.use { readBounded(it, MAX_CONF_BYTES) }
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

    private companion object {
        const val MAX_CONF_BYTES = 65_536L

        fun readBounded(stream: InputStream, maxBytes: Long): String {
            val buf = ByteArray(4096)
            val out = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    throw IOException(".conf слишком большой: > $maxBytes байт")
                }
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
