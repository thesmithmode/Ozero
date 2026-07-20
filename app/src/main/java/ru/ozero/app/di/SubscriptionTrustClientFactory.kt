package ru.ozero.app.di

import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object SubscriptionTrustClientFactory {
    fun createSystem(): OkHttpClient = timeoutBuilder().build()

    fun create(): OkHttpClient {
        val trustManager = systemAndUserTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return timeoutBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }

    private fun timeoutBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)

    private fun systemAndUserTrustManager(): X509TrustManager {
        val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }
}
