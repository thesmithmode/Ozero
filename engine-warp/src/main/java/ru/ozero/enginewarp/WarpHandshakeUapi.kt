package ru.ozero.enginewarp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.File

internal object WarpHandshakeUapi {
    fun check(uapiPath: String, tunnelName: String): Boolean {
        val sockFile = findUapiSocket(uapiPath, tunnelName) ?: return false
        return try {
            val socket = LocalSocket()
            try {
                socket.connect(
                    LocalSocketAddress(sockFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM),
                )
                socket.soTimeout = 50
                socket.outputStream.write("get=1\n\n".toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                socket.inputStream.bufferedReader(Charsets.UTF_8)
                    .lineSequence()
                    .takeWhile { it.isNotEmpty() }
                    .any { line ->
                        line.startsWith("last_handshake_time_sec=") &&
                            (line.substringAfter("=").trim().toLongOrNull() ?: 0L) > 0L
                    }
            } finally {
                runCatching { socket.close() }
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun findUapiSocket(uapiPath: String, tunnelName: String): File? {
        val sockets = File(uapiPath, "sockets")
        val preferred = File(sockets, "$tunnelName.sock")
        if (preferred.exists()) return preferred
        val anySock = sockets.listFiles { f -> f.name.endsWith(".sock") }?.firstOrNull()
        if (anySock != null) return anySock
        val legacy = File(uapiPath, "$tunnelName.sock")
        return if (legacy.exists()) legacy else null
    }
}
