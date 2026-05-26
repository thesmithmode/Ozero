package ru.ozero.commondns

import okhttp3.OkHttpClient
import ru.ozero.enginescore.PersistentLoggers
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class SocksDohResolver(socksPort: Int, endpoint: String = DohResolver.CLOUDFLARE_ENDPOINT, timeoutMs: Long = 5_000L) {
    private val delegate: DohResolver

    init {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        PersistentLoggers.info(TAG, "init via 127.0.0.1:$socksPort → $endpoint")
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .certificatePinner(DohResolver.defaultPinner())
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
            .build()
        delegate = DohResolver(endpoint = endpoint, timeoutMs = timeoutMs, client = client)
    }

    suspend fun resolve(hostname: String): DohResult = delegate.resolve(hostname)

    fun asDnsResolver(): DnsResolver = DnsResolver { resolve(it) }

    private companion object {
        const val TAG = "SocksDohResolver"
    }
}
