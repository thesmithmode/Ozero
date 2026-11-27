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
import java.util.concurrent.atomic.AtomicReference

class TelegramWssProxy internal constructor(
    private val webSockets: WebSocket.Factory,
    private val warpRelay: WarpSocksRelay? = null,
    private val fallbackWebSockets: WebSocket.Factory? = null,
    private val closeTransport: () -> Unit,
) : Closeable {
    private constructor(client: OkHttpClient, warpPort: Int?, fallback: OkHttpClient?) : this(
        client, warpPort?.let(::WarpSocksRelay), fallback, {
            listOfNotNull(client, fallback).forEach {
                it.dispatcher.cancelAll()
                it.connectionPool.evictAll()
                it.dispatcher.executorService.shutdownNow()
            }
        },
    )

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
            .build(),
        warpPort,
        warpPort?.let { port ->
            OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()
        },
    )
    private val preferred = AtomicReference(webSockets)

    private fun transports(): List<WebSocket.Factory> =
        listOfNotNull(preferred.get(), webSockets, fallbackWebSockets).distinct()

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
            if (!Thread.currentThread().isInterrupted) warpRelay?.relay(socket, input, request, header)
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
        // Race hosts and routes under one deadline, committing the MTProto init exactly once.
        if (closed.get() || socket.isClosed) return null
        val routes = destination.hosts.flatMap { host -> transports().map { host to it } }
        val race = RelayRace(socket, init, routes.size)
        val attempts = mutableListOf<WebSocket>()
        var winner: WebSocket? = null
        try {
            for ((host, factory) in routes) {
                if (race.hasWinner() || closed.get() || socket.isClosed) break
                val listener = race.listener(factory)
                try {
                    attempts.add(factory.newWebSocket(request(host), listener))
                } catch (_: RuntimeException) {
                    listener.failedBeforeOpen()
                }
            }
            winner = race.awaitWinner()
            return winner
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            race.expire()
            attempts.filter { it !== winner }.forEach { it.cancel() }
        }
    }

    private inner class RelayRace(
        private val socket: Socket,
        private val init: ByteArray,
        private var remaining: Int,
    ) {
        private val ready = CountDownLatch(1)
        private val lock = Any()
        private var winner: WebSocket? = null
        private var expired = false

        fun hasWinner(): Boolean = synchronized(lock) { winner != null }

        fun listener(factory: WebSocket.Factory) = Attempt(factory)

        inner class Attempt(private val factory: WebSocket.Factory) : WebSocketListener() {
            private var finished = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val accepted = synchronized(lock) {
                    if (finished || expired || winner != null || closed.get() || socket.isClosed ||
                        response.header("Sec-WebSocket-Protocol") != "binary" ||
                        !webSocket.send(init.toByteString())
                    ) {
                        finishAttempt()
                        false
                    } else {
                        winner = webSocket
                        preferred.set(factory)
                        sent.addAndGet(init.size.toLong())
                        ready.countDown()
                        true
                    }
                }
                if (!accepted) webSocket.cancel()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!synchronized(lock) { winner === webSocket }) {
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

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish(webSocket)

            fun failedBeforeOpen() = synchronized(lock) { finishAttempt() }

            private fun terminate(webSocket: WebSocket) {
                finish(webSocket)
                webSocket.cancel()
            }

            private fun finish(webSocket: WebSocket) {
                val committed = synchronized(lock) {
                    finishAttempt()
                    winner === webSocket
                }
                if (committed) release(socket)
            }

            // Must be called under the race lock: callbacks may be synchronous or repeated.
            private fun finishAttempt() {
                if (finished) return
                finished = true
                remaining--
                if (remaining == 0) ready.countDown()
            }
        }

        fun awaitWinner(): WebSocket? {
            ready.await(10, TimeUnit.SECONDS)
            return synchronized(lock) {
                expired = true
                winner
            }
        }

        fun expire() = synchronized(lock) { expired = true }
    }

    internal fun probeRemote(timeoutMs: Long = 5_000): Long? {
        if (closed.get()) return null
        val ready = CountDownLatch(1)
        val lock = Any()
        val started = System.nanoTime()
        val probes = mutableListOf<WebSocket>()
        val factories = transports()
        val candidates = listOf(2, 4, 1, 3, 5).flatMap { dc ->
            TelegramDestination(dc, false).hosts.flatMap { host -> factories.map { host to it } }
        }
        var remaining = candidates.size
        var latency: Long? = null
        var expired = false
        try {
            for ((host, factory) in candidates) {
                if (synchronized(lock) { latency != null || closed.get() }) break
                val done = AtomicBoolean(false)
                fun complete(success: Boolean) {
                    if (!done.compareAndSet(false, true)) return
                    synchronized(lock) {
                        if (success && !expired && !closed.get() && latency == null) {
                            latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                            preferred.set(factory)
                            ready.countDown()
                        }
                        remaining--
                        if (remaining == 0) ready.countDown()
                    }
                }
                try {
                    probes.add(
                        factory.newWebSocket(
                            request(host),
                            object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) {
                                    complete(response.header("Sec-WebSocket-Protocol") == "binary")
                                    webSocket.cancel()
                                }

                                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                                    complete(false)

                                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                                    complete(false)
                            },
                        ),
                    )
                } catch (_: RuntimeException) {
                    complete(false)
                }
            }
            ready.await(timeoutMs, TimeUnit.MILLISECONDS)
            return synchronized(lock) {
                expired = true
                latency.takeUnless { closed.get() }
            }
        } finally {
            synchronized(lock) { expired = true }
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
