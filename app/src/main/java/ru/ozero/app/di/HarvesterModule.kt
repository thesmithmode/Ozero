package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import ru.ozero.coresubscriptions.ServerMapper
import ru.ozero.coresubscriptions.SubscriptionFilter
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HarvesterModule {

    private const val USER_AGENT = "Ozero/1.0 (Android; SubscriptionHarvester)"

    private val userAgentInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        chain.proceed(req)
    }

    @Provides
    @Singleton
    @Named("harvester")
    fun provideHarvesterClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(userAgentInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideSubscriptionUriParser(): SubscriptionUriParser = SubscriptionUriParser()

    @Provides
    @Singleton
    fun provideServerMapper(): ServerMapper = ServerMapper()

    @Provides
    @Singleton
    fun provideSubscriptionFilter(): SubscriptionFilter = SubscriptionFilter()
}
