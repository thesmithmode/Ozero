package ru.ozero.enginewarp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.File

data class WarpUapiState(
    val handshakeAgeSeconds: Long?,
    val rxBytes: Long,
    val txBytes: Long,
    val peersSeen: Int,
)

internal object WarpUapi {

    fun readState(uapiPath: String, tunnelName: String): WarpUapiState? {
        val sockFile = WarpHandshakeUapi.findUapiSocket(uapiPath, tunnelName) ?: return null
        return try {
            querySocket(sockFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun querySocket(sockFile: File): WarpUapiState? {
        val socket = LocalSocket()
        return try {
            socket.connect(
                LocalSocketAddress(sockFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM),
            )
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.outputStream.write(REQUEST_GET_BYTES)
            socket.outputStream.flush()
            parseReply(socket.inputStream.bufferedReader(Charsets.UTF_8))
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun parseReply(reader: BufferedReader): WarpUapiState {
        val nowSec = System.currentTimeMillis() / 1000L
        var rx = 0L
        var tx = 0L
        var lastHsSec: Long? = null
        var peers = 0
        reader.lineSequence()
            .takeWhile { it.isNotEmpty() }
            .forEach { line ->
                applyLine(line) { key, v ->
                    when (key) {
                        "public_key" -> peers++
                        "rx_bytes" -> rx += v
                        "tx_bytes" -> tx += v
                        "last_handshake_time_sec" -> if (v > 0L) lastHsSec = v
                    }
                }
            }
        val age = lastHsSec?.let { (nowSec - it).coerceAtLeast(0L) }
        return WarpUapiState(handshakeAgeSeconds = age, rxBytes = rx, txBytes = tx, peersSeen = peers)
    }

    private inline fun applyLine(line: String, body: (key: String, valueLong: Long) -> Unit) {
        val eq = line.indexOf('=')
        if (eq <= 0) return
        val key = line.substring(0, eq)
        val raw = line.substring(eq + 1)
        val value = raw.trim().toLongOrNull() ?: 0L
        body(key, value)
    }

    private const val SOCKET_TIMEOUT_MS = 100
    private val REQUEST_GET_BYTES = "get=1\n\n".toByteArray(Charsets.UTF_8)
}
