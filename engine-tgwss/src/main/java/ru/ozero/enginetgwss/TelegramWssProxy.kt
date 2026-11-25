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

class TelegramWssProxy internal constructor(
    private val webSockets: WebSocket.Factory,
    private val warpRelay: WarpSocksRelay? = null,
    private val closeTransport: () -> Unit,
) : Closeable {
    private constructor(client: OkHttpClient, warpPort: Int?) : this(client, warpPort?.let(::WarpSocksRelay), {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
    })

    constructor(
        warpPort: Int? = null,
        dns: okhttp3.Dns = okhttp3.Dns.SYSTEM,
        protectSocket: (Socket) -> Boolean,
    ) : this(
        OkHttpClient.Builder()
            .socketFactory(TelegramSocketFactory(protectSocket))
            .dns(dns)
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build(),
        warpPort,
    )
    private val closed = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(0, 128, 30, TimeUnit.SECONDS, SynchronousQueue())
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
        val request = TelegramSocks.readRequest(input, output, fullTunnel = warpRelay != null) ?: return
        val destination = request.telegram
        if (destination == null) {
            val fallback = warpRelay
            if (fallback == null) {
                TelegramSocks.reply(output, 2)
            } else {
                fallback.relay(socket, input, request)
            }
            return
        }
        TelegramSocks.reply(output, 0)
        val header = readHeader(input)
        val init = TelegramInit.prepare(header, destination)
        val ws = init?.let { openRelay(socket, destination, it) }
        if (ws == null) {
            warpRelay?.relay(socket, input, request, header)
            return
        }
        try {
            socket.soTimeout = 0
            val packets = TelegramPacketReader(input, header)
            while (!closed.get() && !socket.isClosed) {
                val packet = packets.readPacket() ?: break
                if (ws.queueSize() + packet.size > 8_388_608 || !ws.send(packet.toByteString())) break
                sent.addAndGet(packet.size.toLong())
            }
        } finally {
            ws.cancel()
        }
    }

    private fun readHeader(input: DataInputStream): ByteArray {
        val buffer = ByteArray(TelegramInit.SIZE)
        var count = 0
        try {
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
        } catch (_: java.net.SocketTimeoutException) {
            return buffer.copyOf(count)
        }
        return buffer.copyOf(count)
    }

    private fun openRelay(socket: Socket, destination: TelegramDestination, init: ByteArray): WebSocket? {
        for (host in destination.hosts) {
            if (closed.get() || socket.isClosed) return null
            val attempt = RelayAttempt(socket, init)
            val ws = webSockets.newWebSocket(request(host), attempt)
            try {
                if (attempt.awaitCommitted()) return ws
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                attempt.expire()
                ws.cancel()
                return null
            }
            ws.cancel()
        }
        return null
    }

    private inner class RelayAttempt(
        private val socket: Socket,
        private val init: ByteArray,
    ) : WebSocketListener() {
        private val ready = CountDownLatch(1)
        private val lock = Any()
        private var committed = false
        private var expired = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                val accepted = !expired && !closed.get() && response.header("Sec-WebSocket-Protocol") == "binary"
                if (accepted && webSocket.send(init.toByteString())) {
                    committed = true
                    sent.addAndGet(init.size.toLong())
                } else {
                    expired = true
                    webSocket.cancel()
                }
                ready.countDown()
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!synchronized(lock) { committed }) {
                terminate(webSocket)
                return
            }
            try {
                socket.getOutputStream().apply {
                    write(bytes.toByteArray())
                    flush()
                }
                received.addAndGet(bytes.size.toLong())
            } catch (_: IOException) {
                terminate(webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = terminate(webSocket)

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = terminate(webSocket)

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish()

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish()

        private fun terminate(webSocket: WebSocket) {
            finish()
            webSocket.cancel()
        }

        private fun finish() {
            synchronized(lock) {
                expired = true
                if (committed) release(socket)
                ready.countDown()
            }
        }

        fun awaitCommitted(): Boolean {
            ready.await(10, TimeUnit.SECONDS)
            return synchronized(lock) {
                expired = true
                committed
            }
        }

        fun expire() {
            synchronized(lock) { expired = true }
        }
    }

    internal fun probeRemote(timeoutMs: Long = 5_000): Long? {
        if (closed.get()) return null
        val ready = CountDownLatch(1)
        val latency = AtomicLong(-1)
        val started = System.nanoTime()
        val probes = mutableListOf<WebSocket>()
        try {
            for (dc in listOf(2, 4, 1)) {
                if (latency.get() >= 0 || closed.get()) break
                val ws = webSockets.newWebSocket(
                    request(TelegramDestination(dc, false).host),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            if (response.header("Sec-WebSocket-Protocol") == "binary") {
                                latency.compareAndSet(
                                    -1,
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                                )
                                ready.countDown()
                            }
                            webSocket.cancel()
                        }
                    },
                )
                probes.add(ws)
            }
            ready.await(timeoutMs, TimeUnit.MILLISECONDS)
            return latency.get().takeIf { it >= 0 && !closed.get() }
        } finally {
            probes.forEach { it.cancel() }
        }
    }

    private fun request(host: String): Request = Request.Builder()
        .url("wss://$host/apiws")
        .header("Origin", "https://web.telegram.org")
        .header("Sec-WebSocket-Protocol", "binary")
        .build()

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
        warpRelay?.close()
        closeTransport()
    }
}
