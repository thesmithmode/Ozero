package ru.ozero.enginetgwss

import java.io.DataInputStream
import java.io.OutputStream

internal object TelegramSocks {
    fun negotiate(input: DataInputStream, output: OutputStream): TelegramDestination? {
        if (input.readUnsignedByte() != 5) return null
        val methods = ByteArray(input.readUnsignedByte())
        input.readFully(methods)
        val accepted = 0.toByte() in methods
        output.write(byteArrayOf(5, if (accepted) 0 else 0xff.toByte()))
        output.flush()
        if (!accepted) return null

        val version = input.readUnsignedByte()
        val command = input.readUnsignedByte()
        val reserved = input.readUnsignedByte()
        val type = input.readUnsignedByte()
        if (version != 5 || reserved != 0) return null
        if (command != 1) {
            reply(output, 7)
            return null
        }
        if (type != 1) {
            reply(output, 8)
            return null
        }
        val ip = ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
        val destination = TelegramDestination.resolve(ip, input.readUnsignedShort())
        reply(output, if (destination == null) 2 else 0)
        return destination
    }

    private fun reply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }
}
