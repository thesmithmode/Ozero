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

/**
 * E16.1: DI для PublicProxyHarvester.
 *
 * `@Named("harvester")` OkHttpClient — без pinning'а (PLAN v4 раздел 0.6:
 * GitHub TLS, system trust, pinning GitHub'а = антипаттерн → brick).
 */
@Module
@InstallIn(SingletonComponent::class)
object HarvesterModule {

    @Provides
    @Singleton
    @Named("harvester")
    fun provideHarvesterClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Hilt MissingBinding fix: ServerImportService.@Inject constructor с default-параметрами
    // не использует defaults — Hilt требует явный binding для каждого аргумента.
    // Эти типы stateless, безопасно как @Singleton.
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
