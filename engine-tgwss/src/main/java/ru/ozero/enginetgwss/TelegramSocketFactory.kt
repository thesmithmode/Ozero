package ru.ozero.enginetgwss

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

internal class TelegramSocketFactory(
    private val protect: (Socket) -> Boolean,
) : SocketFactory() {
    override fun createSocket(): Socket {
        val socket = Socket()
        try {
            if (!protect(socket)) throw IOException("Outbound socket protection failed")
            return socket
        } catch (failure: Throwable) {
            runCatching { socket.close() }
            throw failure
        }
    }

    override fun createSocket(host: String, port: Int): Socket =
        throw IOException("Connected socket creation is not supported")

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        throw IOException("Connected socket creation is not supported")

    override fun createSocket(host: InetAddress, port: Int): Socket =
        throw IOException("Connected socket creation is not supported")

    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        throw IOException("Connected socket creation is not supported")
}
