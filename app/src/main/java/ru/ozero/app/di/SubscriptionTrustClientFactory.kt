package ru.ozero.app.di

import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState

object SubscriptionTrustClientFactory {
    fun createSystem(): OkHttpClient = timeoutBuilder().build()

    fun create(): OkHttpClient {
        val trustManager = systemTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return timeoutBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT,
                ),
            )
            .build()
    }

    fun createInsecure(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return timeoutBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    internal fun forActiveProxy(client: OkHttpClient, controller: TunnelController): OkHttpClient? {
        val state = controller.state.value as? TunnelState.Connected ?: return null
        if (state.socksPort !in 1..65535 || controller.killswitchActive.value || controller.switching.value != null) {
            return null
        }
        return client.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", state.socksPort)))
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun timeoutBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)

    private fun systemTrustManager(): X509TrustManager {
        val systemOnly = systemOnlyCaKeyStore(KeyStore.getInstance("AndroidCAStore").apply { load(null) })
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(systemOnly)
        }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    internal fun systemOnlyCaKeyStore(androidCaStore: KeyStore): KeyStore {
        val systemOnly = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val aliases = androidCaStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (!alias.startsWith(SYSTEM_CA_ALIAS_PREFIX)) continue
            val cert = androidCaStore.getCertificate(alias) ?: continue
            systemOnly.setCertificateEntry(alias, cert)
        }
        return systemOnly
    }

    private const val SYSTEM_CA_ALIAS_PREFIX = "system:"
}
