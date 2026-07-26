package ru.ozero.enginesingbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

fun interface SingboxRoutedProbe {
    suspend fun probeLatencyMs(socksPort: Int): Long

    suspend fun probe(socksPort: Int): RoutedProbeResult =
        probeLatencyMs(socksPort).takeIf { it >= 0 }
            ?.let(RoutedProbeResult::Success)
            ?: RoutedProbeResult.Failure(RoutedProbeResult.Reason.IO)
}

sealed interface RoutedProbeResult {
    data class Success(val latencyMs: Long) : RoutedProbeResult
    data class Failure(val reason: Reason, val safeDetail: String? = null) : RoutedProbeResult

    enum class Reason {
        DNS,
        CONNECT,
        TLS_CERTIFICATE,
        TLS_HANDSHAKE,
        TLS,
        TIMEOUT,
        UNEXPECTED_RESPONSE,
        IO,
        SOCKS_NOT_READY,
    }
}

class SingboxHttp204RoutedProbe(
    private val probeUrl: URL = URL(PROBE_URL),
    private val fallbackProbeUrls: List<URL> = FALLBACK_PROBE_URLS.map(::URL),
    private val socksHost: String = LOOPBACK,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxProbeUrls: Int = DEFAULT_MAX_PROBE_URLS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sslSocketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val hostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
) : SingboxRoutedProbe {

    override suspend fun probeLatencyMs(socksPort: Int): Long = when (val result = probe(socksPort)) {
        is RoutedProbeResult.Success -> result.latencyMs
        is RoutedProbeResult.Failure -> LATENCY_FAILED
    }

    override suspend fun probe(socksPort: Int): RoutedProbeResult = withContext(Dispatchers.IO) {
        probeUntil(socksPort, deadlineNanos = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong()))
    }

    fun probeLatencyMsUntil(socksPort: Int, deadlineNanos: Long): Long =
        when (val result = probeUntil(socksPort, deadlineNanos)) {
            is RoutedProbeResult.Success -> result.latencyMs
            is RoutedProbeResult.Failure -> LATENCY_FAILED
        }

    fun probeUntil(socksPort: Int, deadlineNanos: Long): RoutedProbeResult {
        if (maxProbeUrls <= 0) return RoutedProbeResult.Failure(RoutedProbeResult.Reason.UNEXPECTED_RESPONSE)
        if (socksPort <= 0) {
            PersistentLoggers.warn(TAG, "routed probe failed: invalid socksPort=$socksPort")
            return RoutedProbeResult.Failure(RoutedProbeResult.Reason.SOCKS_NOT_READY)
        }
        if (!isSocksPortReady(socksPort, deadlineNanos)) {
            PersistentLoggers.debug(
                TAG,
                "routed probe failed: socks not ready socksPort=$socksPort timeoutMs=$timeoutMs",
            )
            return RoutedProbeResult.Failure(RoutedProbeResult.Reason.SOCKS_NOT_READY)
        }
        val urls = listOf(probeUrl) + fallbackProbeUrls
        val endpoints = urls.distinctBy { it.toString() }.take(maxProbeUrls)
        val failures = mutableListOf<ProbeFailure>()
        for ((index, url) in endpoints.withIndex()) {
            val remainingTimeoutMs = remainingTimeoutMs(deadlineNanos) ?: break
            val attemptsLeft = endpoints.size - index
            val attemptTimeoutMs = (remainingTimeoutMs / attemptsLeft).coerceAtLeast(1)
            val result = probeSingleUrl(url, socksPort, attemptTimeoutMs)
            val latency = result.latencyMs
            if (latency >= 0) return RoutedProbeResult.Success(latency)
            failures += result.failure
        }
        val failureSummary = if (failures.isEmpty()) {
            "deadline=1"
        } else {
            failures
                .groupingBy(ProbeFailure::label)
                .eachCount()
                .toSortedMap()
                .entries
                .joinToString(",") { (reason, count) -> "$reason=$count" }
        }
        PersistentLoggers.debug(
            TAG,
            "routed probe failed: socksPort=$socksPort timeoutMs=$timeoutMs failures=$failureSummary",
        )
        return RoutedProbeResult.Failure(
            failures.lastOrNull()?.reason ?: RoutedProbeResult.Reason.TIMEOUT,
        )
    }

    private fun isSocksPortReady(socksPort: Int, deadlineNanos: Long): Boolean {
        val connectTimeoutMs = remainingTimeoutMs(deadlineNanos) ?: return false
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(socksHost, socksPort), connectTimeoutMs)
            }
            true
        }.getOrDefault(false)
    }

    private fun probeSingleUrl(url: URL, socksPort: Int, remainingTimeoutMs: Int): ProbeAttempt {
        val expectation = responseExpectation(url)
            ?: return ProbeAttempt(LATENCY_FAILED, ProbeFailure.UNSUPPORTED_RESPONSE)
        val start = nanoTime()
        return runCatching {
            when (url.protocol.lowercase()) {
                "https" -> probeHttpsOverSocks(url, socksPort, remainingTimeoutMs, expectation)
                "http" -> probeHttpOverSocks(url, socksPort, remainingTimeoutMs, expectation)
                else -> false
            }
        }.fold(
            onSuccess = { success ->
                if (success) {
                    ProbeAttempt(TimeUnit.NANOSECONDS.toMillis(nanoTime() - start).coerceAtLeast(1L), ProbeFailure.NONE)
                } else {
                    ProbeAttempt(LATENCY_FAILED, ProbeFailure.UNEXPECTED_RESPONSE)
                }
            },
            onFailure = { error -> ProbeAttempt(LATENCY_FAILED, classifyFailure(error)) },
        )
    }

    private fun probeHttpOverSocks(
        url: URL,
        socksPort: Int,
        remainingTimeoutMs: Int,
        expectation: ResponseExpectation,
    ): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        return Socket(proxy).use { socket ->
            val destinationPort = url.port.takeIf { it > 0 } ?: HTTP_PORT
            socket.connect(InetSocketAddress.createUnresolved(url.host, destinationPort), remainingTimeoutMs)
            socket.soTimeout = remainingTimeoutMs
            writeHttpGet(socket, url, destinationPort, HTTP_PORT)
            val response = readRawHttpResponse(BufferedInputStream(socket.getInputStream())) ?: return@use false
            expectation.matches(response)
        }
    }

    private fun probeHttpsOverSocks(
        url: URL,
        socksPort: Int,
        remainingTimeoutMs: Int,
        expectation: ResponseExpectation,
    ): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
        Socket(proxy).use { tcpSocket ->
            val destinationPort = url.port.takeIf { it > 0 } ?: HTTPS_PORT
            tcpSocket.connect(InetSocketAddress.createUnresolved(url.host, destinationPort), remainingTimeoutMs)
            tcpSocket.soTimeout = remainingTimeoutMs
            val sslSocket = sslSocketFactory.createSocket(tcpSocket, url.host, destinationPort, true) as SSLSocket
            sslSocket.use { socket ->
                socket.soTimeout = remainingTimeoutMs
                socket.startHandshake()
                if (!hostnameVerifier.verify(url.host, socket.session)) {
                    throw SSLPeerUnverifiedException("Hostname verification failed")
                }
                writeHttpGet(socket, url, destinationPort, HTTPS_PORT)
                val response = readRawHttpResponse(BufferedInputStream(socket.getInputStream())) ?: return false
                return expectation.matches(response)
            }
        }
    }

    private fun writeHttpGet(socket: Socket, url: URL, destinationPort: Int, defaultPort: Int) {
        val requestTarget = url.file.ifEmpty { "/" }
        val hostHeader = if (destinationPort == defaultPort) url.host else "${url.host}:$destinationPort"
        val request = buildString {
            append("GET $requestTarget HTTP/1.1\r\n")
            append("Host: $hostHeader\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(request.toByteArray(StandardCharsets.US_ASCII))
            flush()
        }
    }

    private fun readRawHttpResponse(input: BufferedInputStream): RawHttpResponse? {
        val statusLine = input.readAsciiLine(MAX_HTTP_LINE_BYTES) ?: return null
        val statusParts = statusLine.split(' ', limit = 3)
        if (statusParts.firstOrNull() !in SUPPORTED_HTTP_VERSIONS) return null
        val statusCode = statusParts.getOrNull(1)?.toIntOrNull() ?: return null
        repeat(MAX_HTTP_HEADER_LINES) {
            val line = input.readAsciiLine(MAX_HTTP_LINE_BYTES) ?: return null
            if (line.isEmpty()) {
                return RawHttpResponse(statusCode, input.readBody(MAX_HTTP_BODY_BYTES))
            }
        }
        return null
    }

    private fun BufferedInputStream.readAsciiLine(maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        while (output.size() <= maxBytes) {
            val next = read()
            if (next < 0) return null
            if (next == '\n'.code) {
                val bytes = output.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, length, StandardCharsets.US_ASCII)
            }
            output.write(next)
        }
        return null
    }

    private fun BufferedInputStream.readBody(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        while (output.size() < maxBytes) {
            val next = read()
            if (next < 0) break
            output.write(next)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun responseExpectation(url: URL): ResponseExpectation? = when {
        url.path.endsWith(GENERATE_204_PATH) -> ResponseExpectation.NO_CONTENT
        url.host.equals(MSFT_CONNECT_TEST_HOST, ignoreCase = true) && url.path == MSFT_CONNECT_TEST_PATH ->
            ResponseExpectation.MSFT_CONNECT_TEST
        else -> null
    }

    private fun classifyFailure(error: Throwable): ProbeFailure {
        val causes = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        return when {
            causes.any { it is UnknownHostException } -> ProbeFailure.DNS
            causes.any {
                it is CertificateException || it is SSLPeerUnverifiedException
            } -> ProbeFailure.TLS_CERTIFICATE
            causes.any { it is SSLHandshakeException } -> ProbeFailure.TLS_HANDSHAKE
            causes.any { it is SocketTimeoutException } -> ProbeFailure.TIMEOUT
            causes.any { it is ConnectException } -> ProbeFailure.CONNECT
            causes.any { it is SSLException } -> ProbeFailure.TLS
            causes.any { it is IOException } -> ProbeFailure.IO
            else -> ProbeFailure.UNEXPECTED_ERROR
        }
    }

    private enum class ResponseExpectation {
        NO_CONTENT,
        MSFT_CONNECT_TEST,
        ;

        fun matches(response: RawHttpResponse): Boolean = when (this) {
            NO_CONTENT -> response.statusCode == HTTP_NO_CONTENT
            MSFT_CONNECT_TEST ->
                response.statusCode == HTTP_OK && response.body.startsWith(MSFT_CONNECT_TEST_BODY)
        }
    }

    private enum class ProbeFailure(val label: String, val reason: RoutedProbeResult.Reason) {
        NONE("none", RoutedProbeResult.Reason.IO),
        DNS("dns", RoutedProbeResult.Reason.DNS),
        TLS_CERTIFICATE("tls-certificate", RoutedProbeResult.Reason.TLS_CERTIFICATE),
        TLS_HANDSHAKE("tls-handshake", RoutedProbeResult.Reason.TLS_HANDSHAKE),
        TLS("tls", RoutedProbeResult.Reason.TLS),
        TIMEOUT("timeout", RoutedProbeResult.Reason.TIMEOUT),
        CONNECT("connect", RoutedProbeResult.Reason.CONNECT),
        IO("io", RoutedProbeResult.Reason.IO),
        UNEXPECTED_RESPONSE("unexpected-response", RoutedProbeResult.Reason.UNEXPECTED_RESPONSE),
        UNSUPPORTED_RESPONSE("unsupported-response", RoutedProbeResult.Reason.UNEXPECTED_RESPONSE),
        UNEXPECTED_ERROR("unexpected-error", RoutedProbeResult.Reason.IO),
    }

    private data class RawHttpResponse(val statusCode: Int, val body: String)

    private data class ProbeAttempt(val latencyMs: Long, val failure: ProbeFailure)

    companion object {
        private const val TAG = "SingboxRoutedProbe"
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
        val FALLBACK_PROBE_URLS = listOf(
            "https://cp.cloudflare.com/generate_204",
            "http://www.msftconnecttest.com/connecttest.txt",
        )
        const val LATENCY_FAILED = -1L
        private const val LOOPBACK = "127.0.0.1"
        private const val DEFAULT_TIMEOUT_MS = 12_000
        private const val DEFAULT_MAX_PROBE_URLS = 3
        private const val GENERATE_204_PATH = "/generate_204"
        private const val MSFT_CONNECT_TEST_HOST = "www.msftconnecttest.com"
        private const val MSFT_CONNECT_TEST_PATH = "/connecttest.txt"
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
        private const val HTTP_OK = 200
        private const val HTTP_NO_CONTENT = 204
        private const val MSFT_CONNECT_TEST_BODY = "Microsoft Connect Test"
        private const val MAX_HTTP_LINE_BYTES = 1_024
        private const val MAX_HTTP_HEADER_LINES = 64
        private const val MAX_HTTP_BODY_BYTES = 256
        private const val MAX_CAUSE_DEPTH = 8
        private val SUPPORTED_HTTP_VERSIONS = setOf("HTTP/1.0", "HTTP/1.1")
    }

    private fun remainingTimeoutMs(deadlineNanos: Long): Int? {
        val remainingNanos = deadlineNanos - nanoTime()
        if (remainingNanos <= 0) return null
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos)
            .coerceAtLeast(1L)
            .coerceAtMost(timeoutMs.toLong())
            .toInt()
    }
}
