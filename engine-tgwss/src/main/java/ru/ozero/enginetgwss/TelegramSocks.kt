package ru.ozero.enginetgwss

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress

internal data class SocksRequest(val command: Int, val address: ByteArray, val port: Int, val type: Int) {
    val telegram: TelegramDestination?
        get() = if (command == 1 && type == 1) {
            TelegramDestination.resolve(InetAddress.getByAddress(address).hostAddress.orEmpty(), port)
        } else {
            null
        }

    fun encode(): ByteArray = byteArrayOf(5, command.toByte(), 0, type.toByte()) +
        (if (type == 3) byteArrayOf(address.size.toByte()) else byteArrayOf()) +
        address + byteArrayOf((port shr 8).toByte(), port.toByte())
}

internal object TelegramSocks {
    fun negotiate(input: DataInputStream, output: OutputStream): TelegramDestination? {
        val request = readRequest(input, output, fullTunnel = false) ?: return null
        val destination = request.telegram
        reply(output, if (destination == null) 2 else 0)
        return destination
    }

    fun readRequest(input: DataInputStream, output: OutputStream, fullTunnel: Boolean): SocksRequest? {
        if (input.readUnsignedByte() != 5) return null
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val accepted = 0.toByte() in methods
        output.write(byteArrayOf(5, if (accepted) 0 else 0xff.toByte()))
        output.flush()
        if (!accepted) return null
        val version = input.readUnsignedByte()
        val command = input.readUnsignedByte()
        val reserved = input.readUnsignedByte()
        val type = input.readUnsignedByte()
        if (version != 5 || reserved != 0) return null
        if (command != 1 && (!fullTunnel || command != 3)) {
            reply(output, 7)
            return null
        }
        val size = when (type) {
            1 -> 4
            3 -> if (fullTunnel) input.readUnsignedByte() else 0
            4 -> if (fullTunnel) 16 else 0
            else -> 0
        }
        if (size == 0) {
            reply(output, 8)
            return null
        }
        val address = ByteArray(size).also { input.readFully(it) }
        return SocksRequest(command, address, input.readUnsignedShort(), type)
    }

    fun reply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }
}
