package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
}
