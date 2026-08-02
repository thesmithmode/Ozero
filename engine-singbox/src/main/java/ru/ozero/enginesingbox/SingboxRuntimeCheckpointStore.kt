package ru.ozero.enginesingbox

import android.os.Process
import ru.ozero.enginescore.LogSanitizer
import java.io.File
import java.io.FileOutputStream
import java.nio.file.StandardCopyOption

object SingboxRuntimeCheckpointStore {
    private const val FILE_NAME = "runtime-checkpoints.log"
    private const val MAX_LINES = 64
    private const val ROTATE_BYTES = 64 * 1024L
    private val lock = Any()

    fun record(
        directory: File,
        message: String,
        timestamp: Long = System.currentTimeMillis(),
        pid: Int = Process.myPid(),
    ) {
        val safe = LogSanitizer.sanitize(message).replace(Regex("\\s+"), " ").take(1_000)
        synchronized(lock) {
            directory.mkdirs()
            val file = File(directory, FILE_NAME)
            FileOutputStream(file, true).use { output ->
                output.write("$pid|$timestamp|$safe\n".toByteArray(Charsets.UTF_8))
            }
            if (file.length() > ROTATE_BYTES) rotate(file)
        }
    }

    fun read(directory: File, pid: Int): List<String> = synchronized(lock) {
        File(directory, FILE_NAME)
            .takeIf { it.isFile }
            ?.readLines(Charsets.UTF_8)
            ?.filter { it.startsWith("$pid|") }
            ?.takeLast(MAX_LINES)
            .orEmpty()
    }

    private fun rotate(file: File) {
        val retained = file.readLines(Charsets.UTF_8).takeLast(MAX_LINES)
        val replacement = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(replacement).use { output ->
            output.write(retained.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
        }
        runCatching {
            java.nio.file.Files.move(
                replacement.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure {
            file.writeText(retained.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            replacement.delete()
        }
    }
}
