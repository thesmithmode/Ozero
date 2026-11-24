package ru.ozero.commonnet

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

internal object StunBinding {
    private const val COOKIE = 0x2112A442
    private const val HEADER_SIZE = 20
    private const val TRANSACTION_SIZE = 12
    private const val XOR_MAPPED_ADDRESS = 0x0020

    fun request(transaction: ByteArray): ByteArray {
        require(transaction.size == TRANSACTION_SIZE)
        return ByteBuffer.allocate(HEADER_SIZE)
            .putShort(0x0001)
            .putShort(0)
            .putInt(COOKIE)
            .put(transaction)
            .array()
    }

    fun mappedAddress(packet: ByteArray, transaction: ByteArray): InetSocketAddress? {
        if (packet.size < HEADER_SIZE || transaction.size != TRANSACTION_SIZE) return null
        val input = ByteBuffer.wrap(packet)
        val type = input.short.toInt() and 0xffff
        val length = input.short.toInt() and 0xffff
        if (type != 0x0101 || length != packet.size - HEADER_SIZE || length % 4 != 0) return null
        if (input.int != COOKIE || !packet.copyOfRange(8, HEADER_SIZE).contentEquals(transaction)) return null
        input.position(HEADER_SIZE)
        while (input.remaining() >= 4) {
            val attribute = input.short.toInt() and 0xffff
            val size = input.short.toInt() and 0xffff
            val paddedSize = (size + 3) and 3.inv()
            if (paddedSize > input.remaining()) return null
            if (attribute == XOR_MAPPED_ADDRESS) {
                return decodeAddress(input, size, transaction)
            }
            input.position(input.position() + paddedSize)
        }
        return null
    }

    private fun decodeAddress(input: ByteBuffer, size: Int, transaction: ByteArray): InetSocketAddress? {
        if (size < 4 || input.get().toInt() != 0) return null
        val addressSize = when (input.get().toInt()) {
            1 -> 4
            2 -> 16
            else -> return null
        }
        if (size != addressSize + 4) return null
        val port = (input.short.toInt() and 0xffff) xor (COOKIE ushr 16)
        if (port == 0) return null
        val mask = ByteBuffer.allocate(16).putInt(COOKIE).put(transaction).array()
        val bytes = ByteArray(addressSize) { index ->
            (input.get().toInt() xor mask[index].toInt()).toByte()
        }
        return InetSocketAddress(InetAddress.getByAddress(bytes), port)
    }
}
