package ru.ozero.enginetgwss

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

internal class FakeTelegramWebSockets(
    @Volatile var protocol: String? = "binary",
    private val connectFails: Boolean = false,
    private val rejectedHosts: Set<String> = emptySet(),
) : WebSocket.Factory {
    val sessions = LinkedBlockingQueue<Session>()

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val session = Session(request, listener)
        sessions.add(session)
        if (connectFails || request.url.host in rejectedHosts) {
            listener.onFailure(session, java.io.IOException("test connection failure"), null)
        } else {
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .apply { protocol?.let { header("Sec-WebSocket-Protocol", it) } }
                .build()
            listener.onOpen(session, response)
        }
        return session
    }

    internal class Session(
        private val originalRequest: Request,
        val listener: WebSocketListener,
    ) : WebSocket {
        val sent = LinkedBlockingQueue<ByteString>()
        val cancelled = AtomicBoolean(false)

        override fun request(): Request = originalRequest
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = false
        override fun send(bytes: ByteString): Boolean = sent.offer(bytes)
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() {
            cancelled.set(true)
        }
    }
}
