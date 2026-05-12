package ru.ozero.app.logging

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal object LogFileStore {

    private const val DIR = "logs"
    private const val FILE = "ozero.log"
    private const val PREV = "ozero.log.prev"
    const val MAX_BYTES = 5_000_000L

    private val targetRef = AtomicReference<File?>(null)

    @Synchronized
    fun init(context: Context): File? {
        targetRef.get()?.let { return it }
        return runCatching {
            val dir = resolveDir(context)
            dir.mkdirs()
            val file = File(dir, FILE)
            rotateIfTooLarge(file)
            targetRef.set(file)
            file
        }.getOrNull()
    }

    fun current(): File? = targetRef.get()

    fun prev(): File? {
        val parent = targetRef.get()?.parentFile ?: return null
        return File(parent, PREV)
    }

    @Synchronized
    fun clear() {
        current()?.let { runCatching { it.writeText("") } }
        prev()?.let { runCatching { if (it.exists()) it.delete() } }
    }

    fun totalSize(): Long {
        val cur = current()?.takeIf { it.exists() }?.length() ?: 0L
        val p = prev()?.takeIf { it.exists() }?.length() ?: 0L
        return cur + p
    }

    fun rotateIfTooLarge(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val prev = File(file.parentFile, PREV)
        runCatching {
            if (prev.exists()) prev.delete()
            file.renameTo(prev)
            file.createNewFile()
        }
    }

    private fun resolveDir(context: Context): File {
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return if (external != null) File(external, DIR) else File(context.filesDir, DIR)
    }
}
