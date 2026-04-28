package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    @Provides
    @Singleton
    @Named("harvester")
    fun provideHarvesterClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
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
