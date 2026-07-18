package ru.ozero.app.di

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object SubscriptionTrustClientFactory {
    fun createSystem(): OkHttpClient = timeoutBuilder().build()

    private fun timeoutBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
}
