package ru.ozero.enginetgwss

import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class WarpSocksRelay(private val port: Int) : Closeable {
    private val active = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean(false)

    fun relay(client: Socket, input: DataInputStream, request: SocksRequest, prefix: ByteArray? = null) {
        val output = client.getOutputStream()
        val upstream = Socket()
        active.add(upstream)
        var replySent = prefix != null
        try {
            if (closed.get()) throw IOException("WARP relay stopped")
            upstream.connect(InetSocketAddress(LOOPBACK, port), TIMEOUT_MS)
            upstream.soTimeout = TIMEOUT_MS
            val upstreamInput = DataInputStream(upstream.getInputStream())
            val upstreamOutput = upstream.getOutputStream()
            upstreamOutput.write(byteArrayOf(5, 1, 0))
            upstreamOutput.flush()
            if (upstreamInput.readUnsignedByte() != 5 || upstreamInput.readUnsignedByte() != 0) {
                throw IOException("WARP SOCKS authentication failed")
            }
            val upstreamRequest = if (request.command == 3) SocksRequest(3, ByteArray(4), 0, 1) else request
            upstreamOutput.write(upstreamRequest.encode())
            upstreamOutput.flush()
            val response = readResponse(upstreamInput, request.command)
            if (prefix == null) {
                replySent = true
                output.write(response)
                output.flush()
            }
            if (response[1].toInt() != 0) return
            if (request.command == 3) {
                holdUdpAssociation(client, input, upstream, upstreamInput)
                return
            }
            if (prefix != null) {
                upstreamOutput.write(prefix)
                upstreamOutput.flush()
            }
            client.soTimeout = 0
            upstream.soTimeout = 0
            val down = Thread {
                try {
                    upstreamInput.copyTo(output)
                    runCatching { client.shutdownOutput() }
                } catch (_: IOException) {
                    runCatching { client.close() }
                }
            }
            down.name = "tgwss-warp-down"
            down.isDaemon = true
            down.start()
            try {
                input.copyTo(upstreamOutput)
                runCatching { upstream.shutdownOutput() }
                down.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                runCatching { upstream.close() }
                runCatching { client.close() }
            }
        } catch (_: IOException) {
            if (!replySent && !client.isClosed) {
                runCatching { TelegramSocks.reply(output, 1) }
            }
        } finally {
            runCatching { upstream.close() }
            active.remove(upstream)
        }
    }

    private fun holdUdpAssociation(
        client: Socket,
        input: DataInputStream,
        upstream: Socket,
        upstreamInput: DataInputStream,
    ) {
        client.soTimeout = 0
        upstream.soTimeout = 0
        val watcher = Thread {
            try {
                upstreamInput.read()
            } catch (_: IOException) {
                Unit
            } finally {
                runCatching { client.close() }
            }
        }
        watcher.isDaemon = true
        watcher.start()
        try {
            input.read()
        } finally {
            runCatching { upstream.close() }
        }
    }

    override fun close() {
        closed.set(true)
        active.forEach { runCatching { it.close() } }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private val LOOPBACK = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

        private fun validateUdpAddress(type: Int, address: ByteArray, port: Int) {
            if (type != 1 || port == 0) throw IOException("Unsupported WARP UDP relay")
            if (address.all { it == 0.toByte() }) LOOPBACK.address.copyInto(address)
            if (!InetAddress.getByAddress(address).isLoopbackAddress) {
                throw IOException("WARP UDP relay is not local")
            }
        }

        internal fun readResponse(input: DataInputStream, command: Int): ByteArray {
            val version = input.readUnsignedByte()
            val status = input.readUnsignedByte()
            val reserved = input.readUnsignedByte()
            val type = input.readUnsignedByte()
            if (version != 5 || reserved != 0) throw IOException("Invalid upstream SOCKS response")
            val size = when (type) {
                1 -> 4
                4 -> 16
                3 -> input.readUnsignedByte()
                else -> throw IOException("Invalid upstream SOCKS address")
            }
            if (size == 0) throw IOException("Empty upstream SOCKS address")
            val address = ByteArray(size).also { input.readFully(it) }
            val port = input.readUnsignedShort()
            if (command == 3 && status == 0) {
                validateUdpAddress(type, address, port)
            }
            return byteArrayOf(5, status.toByte(), 0, type.toByte()) +
                (if (type == 3) byteArrayOf(size.toByte()) else byteArrayOf()) +
                address + byteArrayOf((port shr 8).toByte(), port.toByte())
        }
    }
}
