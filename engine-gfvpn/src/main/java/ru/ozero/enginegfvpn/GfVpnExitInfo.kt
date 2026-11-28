package ru.ozero.enginegfvpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import ru.ozero.commonnet.IpInfo
import ru.ozero.commonnet.OkHttpIpInfoProvider
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** Resolve the exit using the SDK's authenticated SOCKS route, never the excluded app's network. */
internal object GfVpnExitInfo {
    suspend fun fetch(session: GfVpnRuntime.Session): Result<IpInfo> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .socketFactory(GfVpnExitSocketFactory(session.port, session.username, session.password))
            // The SOCKS server resolves the actual destination. No direct DNS lookup is necessary.
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
            })
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        try {
            OkHttpIpInfoProvider(client = client).fetch()
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}

internal class GfVpnExitSocketFactory(
    private val port: Int,
    private val username: String,
    private val password: String,
) : SocketFactory() {
    override fun createSocket(): Socket = object : Socket(Proxy.NO_PROXY) {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val target = endpoint as? InetSocketAddress ?: throw IOException("Invalid exit lookup address")
            val host = target.hostString
            if (target.port != 443 || host !in ALLOWED_HOSTS) throw IOException("Invalid exit lookup host")
            try {
                super.connect(InetSocketAddress("127.0.0.1", this@GfVpnExitSocketFactory.port), timeout)
                val previous = soTimeout
                soTimeout = 4000
                try {
                    GfVpnSocksProbe.connect(this, username, password, host, target.port)
                } finally {
                    soTimeout = previous
                }
            } catch (failure: Exception) {
                runCatching { close() }
                throw failure
            }
        }
    }

    override fun createSocket(host: String, port: Int): Socket = unsupported()
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = unsupported()
    override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        unsupported()

    private fun unsupported(): Nothing = throw IOException("Connected socket creation is unsupported")

    private companion object {
        val ALLOWED_HOSTS = setOf("ifconfig.co", "ipapi.co")
    }
}
