package ru.ozero.singboxprocess

import android.os.ParcelFileDescriptor
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginescore.PersistentLoggers

internal class SingboxProtectorBridge(
    private val aidlProtector: ISingboxProtector,
) {
    fun protect(fd: Int): Boolean = runCatching {
        ParcelFileDescriptor.fromFd(fd).use { socket ->
            aidlProtector.protect(socket)
        }
    }.onFailure { failure ->
        PersistentLoggers.warn(
            TAG,
            "protect failed stableCategory=ipc exceptionClass=${failure::class.java.simpleName}",
        )
    }.getOrDefault(false)

    private companion object {
        const val TAG = "SingboxProtectorBridge"
    }
}
