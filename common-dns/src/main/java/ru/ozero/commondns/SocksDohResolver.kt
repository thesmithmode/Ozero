package ru.ozero.commondns

import android.util.Log
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * DoH-резолвер, идущий через локальный SOCKS5-прокси (например, активный Xray).
 *
 * Используется как primary резолвер в [DnsResolverChain] когда работает Xray:
 * DNS-запрос инкапсулируется в Xray-туннель и не утекает в обход.
 *
 * Privacy: hostname НЕ логируется. Только endpoint и socksPort.
 */
class SocksDohResolver(socksPort: Int, endpoint: String = DohResolver.CLOUDFLARE_ENDPOINT, timeoutMs: Long = 5_000L) {
    private val delegate: DohResolver

    init {
        require(socksPort in 1..65535) { "socksPort вне диапазона: $socksPort" }
        Log.i(TAG, "init via 127.0.0.1:$socksPort → $endpoint")
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
