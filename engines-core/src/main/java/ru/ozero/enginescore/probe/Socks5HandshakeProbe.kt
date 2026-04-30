package ru.ozero.enginescore.probe

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object Socks5HandshakeProbe {

    private const val TAG = "Socks5HandshakeProbe"
    private const val SOCKS_VERSION: Byte = 0x05
    private const val METHOD_NO_AUTH: Byte = 0x00

    suspend fun probe(host: String, port: Int, timeoutMs: Int = 3_000): Long =
        withContext(Dispatchers.IO) {
            require(port in 1..65535) { "port out of range: $port" }
            require(timeoutMs > 0) { "timeoutMs must be > 0" }

            val started = System.currentTimeMillis()
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                try {
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                } catch (e: IOException) {
                    Log.w(TAG, "connect failed $host:$port: ${e.message}")
                    throw e
                }

                val out = socket.getOutputStream()
                out.write(byteArrayOf(SOCKS_VERSION, 0x01, METHOD_NO_AUTH))
                out.flush()

                val ins = socket.getInputStream()
                val ver = ins.read()
                val method = ins.read()
                if (ver != 0x05) throw IOException("bad SOCKS version in response: $ver")
                if (method == 0xFF) throw IOException("server rejected all auth methods")
                if (method != 0x00) throw IOException("unsupported method=$method (expected 0x00)")
                val latency = System.currentTimeMillis() - started
                Log.d(TAG, "ok $host:$port latency=${latency}ms")
                latency
            }
        }
}
