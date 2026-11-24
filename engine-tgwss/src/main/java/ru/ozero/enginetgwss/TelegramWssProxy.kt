package ru.ozero.enginetgwss

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TelegramWssProxy(
    protectSocket: (Socket) -> Boolean,
) : Closeable {
    private val http = OkHttpClient.Builder()
        .socketFactory(TelegramSocketFactory(protectSocket))
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val closed = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS, SynchronousQueue())
    private val acceptor = Executors.newSingleThreadExecutor()
    private var listener: ServerSocket? = null
    private val sent = AtomicLong()
    private val received = AtomicLong()

    val bytesOut: Long get() = sent.get()
    val bytesIn: Long get() = received.get()
    val activeConnections: Int get() = sockets.size

    @Synchronized
    fun start(port: Int = 0): Int {
        check(!closed.get())
        check(listener == null)
        require(port in 0..65535)
        val server = ServerSocket()
        try {
            server.bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port))
            listener = server
            acceptor.execute { accept(server) }
            return server.localPort
        } catch (failure: Exception) {
            server.close()
            throw failure
        }
    }

    private fun accept(server: ServerSocket) {
        while (!closed.get()) {
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            sockets.add(socket)
            if (closed.get()) {
                release(socket)
                return
            }
            try {
                workers.execute {
                    try {
                        relay(socket)
                    } catch (_: IOException) {
                        Unit
                    } finally {
                        release(socket)
                    }
                }
            } catch (_: RejectedExecutionException) {
                release(socket)
            }
        }
    }

    private fun relay(socket: Socket) {
        socket.soTimeout = 10_000
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val destination = TelegramSocks.negotiate(input, output) ?: return
        val header = ByteArray(TelegramInit.SIZE).also { input.readFully(it) }
        val init = TelegramInit.prepare(header, destination) ?: return
        val ready = CountDownLatch(1)
        val opened = AtomicBoolean(false)
        val request = Request.Builder()
            .url("wss://${destination.host}/apiws")
            .header("Origin", "https://web.telegram.org")
            .header("Sec-WebSocket-Protocol", "binary")
            .build()
        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (response.header("Sec-WebSocket-Protocol") == "binary" && webSocket.send(init.toByteString())) {
                    opened.set(true)
                    sent.addAndGet(init.size.toLong())
                } else {
                    webSocket.cancel()
                    release(socket)
                }
                ready.countDown()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    output.write(bytes.toByteArray())
                    output.flush()
                    received.addAndGet(bytes.size.toLong())
                } catch (_: IOException) {
                    webSocket.cancel()
                    release(socket)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.cancel()
                release(socket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                release(socket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                release(socket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                release(socket)
                ready.countDown()
            }
        })
        try {
            if (!ready.await(10, TimeUnit.SECONDS) || !opened.get()) return
            socket.soTimeout = 0
            val packets = TelegramPacketReader(input, header)
            while (!closed.get() && !socket.isClosed) {
                val packet = packets.readPacket() ?: break
                if (ws.queueSize() + packet.size > 8_388_608 || !ws.send(packet.toByteString())) break
                sent.addAndGet(packet.size.toLong())
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            ws.cancel()
        }
    }

    private fun release(socket: Socket) {
        runCatching { socket.close() }
        sockets.remove(socket)
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { listener?.close() }
        sockets.forEach(::release)
        acceptor.shutdownNow()
        workers.shutdownNow()
    }
}
