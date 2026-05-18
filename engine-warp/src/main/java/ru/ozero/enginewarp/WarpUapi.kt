package ru.ozero.enginewarp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.File

data class WarpUapiState(
    val handshakeAgeSeconds: Long?,
    val rxBytes: Long,
    val txBytes: Long,
    val peersSeen: Int,
)

internal object WarpUapi {

    fun readState(uapiPath: String, tunnelName: String): WarpUapiState? {
        val sockFile = File(uapiPath, "$tunnelName.sock")
        if (!sockFile.exists()) return null
        val nowSec = System.currentTimeMillis() / 1000L
        return try {
            val socket = LocalSocket()
            try {
                socket.connect(
                    LocalSocketAddress(sockFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM),
                )
                socket.soTimeout = 100
                socket.outputStream.write("get=1\n\n".toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                var rx = 0L
                var tx = 0L
                var lastHsSec: Long? = null
                var peers = 0
                socket.inputStream.bufferedReader(Charsets.UTF_8)
                    .lineSequence()
                    .takeWhile { it.isNotEmpty() }
                    .forEach { line ->
                        when {
                            line.startsWith("public_key=") -> peers++
                            line.startsWith("rx_bytes=") -> rx += valueLong(line)
                            line.startsWith("tx_bytes=") -> tx += valueLong(line)
                            line.startsWith("last_handshake_time_sec=") -> {
                                val hs = valueLong(line)
                                if (hs > 0L) lastHsSec = hs
                            }
                        }
                    }
                val age = lastHsSec?.let { (nowSec - it).coerceAtLeast(0L) }
                WarpUapiState(handshakeAgeSeconds = age, rxBytes = rx, txBytes = tx, peersSeen = peers)
            } finally {
                runCatching { socket.close() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun valueLong(line: String): Long =
        line.substringAfter("=").trim().toLongOrNull() ?: 0L
}
