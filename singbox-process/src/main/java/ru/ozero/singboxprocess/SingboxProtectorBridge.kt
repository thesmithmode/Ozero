package ru.ozero.singboxprocess

import android.os.ParcelFileDescriptor
import android.os.Process
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginescore.PersistentLoggers

internal class SingboxProtectorBridge(
    private val aidlProtector: ISingboxProtector,
) {
    fun protect(fd: Int): Boolean = runCatching {
        ParcelFileDescriptor.fromFd(fd).use { socket ->
            PersistentLoggers.debug(
                TAG,
                "protect request sourcePid=${Process.myPid()} sourceDupFd=${socket.fd}",
            )
            aidlProtector.protect(socket)
        }
    }.getOrDefault(false).also { result ->
        PersistentLoggers.debug(TAG, "protect request sourcePid=${Process.myPid()} result=$result")
    }

    private companion object {
        const val TAG = "SingboxProtectorBridge"
    }
}
