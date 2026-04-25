package ru.ozero.coreorchestrator.probe

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Валидационный probe локального SOCKS5-листенера.
 *
 * TCP connect недостаточен: порт может быть занят чужим процессом, либо engine
 * упал в half-open состоянии (слушает, но handshake не отвечает). Поэтому шлём
 * минимальный SOCKS5 greeting (RFC 1928) с методом NO_AUTH и проверяем ответ.
 *
 * Возвращает latency в мс. Любая сетевая или протокольная ошибка → IOException.
 */
object Socks5HandshakeProbe {

    private const val TAG = "Socks5HandshakeProbe"
    private const val SOCKS_VERSION: Byte = 0x05
    private const val METHOD_NO_AUTH: Byte = 0x00

    suspend fun probe(host: String, port: Int, timeoutMs: Int = 3_000): Long =
        withContext(Dispatchers.IO) {
            require(port in 1..65535) { "port вне диапазона: $port" }
            require(timeoutMs > 0) { "timeoutMs должен быть > 0" }

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
                // VER=5, NMETHODS=1, METHOD=0x00 (NO AUTH)
                out.write(byteArrayOf(SOCKS_VERSION, 0x01, METHOD_NO_AUTH))
                out.flush()

                val ins = socket.getInputStream()
                val ver = ins.read()
                val method = ins.read()
                if (ver != 0x05) {
                    throw IOException("неверная версия SOCKS в ответе: $ver")
                }
                if (method == 0xFF) {
                    throw IOException("сервер отверг все методы аутентификации")
                }
                if (method != 0x00) {
                    throw IOException("неподдерживаемый method=$method (ожидался 0x00)")
                }
                val latency = System.currentTimeMillis() - started
                Log.d(TAG, "ok $host:$port latency=${latency}ms")
                latency
            }
        }
}
