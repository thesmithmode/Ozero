package ru.ozero.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ozero.app.proxy.TelegramProxyCoordinator
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginetelegram.DataStoreTelegramConfigStore
import ru.ozero.enginetelegram.TelegramConfigStore
import ru.ozero.enginetelegram.TelegramProxyService
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramProxyPrefs

@Module
@InstallIn(SingletonComponent::class)
object TelegramProxyModule {

    @Provides
    @Singleton
    @TelegramProxyPrefs
    fun provideTelegramDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("telegram_proxy_prefs") },
        )

    @Provides
    @Singleton
    fun provideTelegramConfigStore(
        @TelegramProxyPrefs dataStore: DataStore<Preferences>,
    ): TelegramConfigStore = DataStoreTelegramConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideTelegramProxyService(
        @ApplicationContext context: Context,
        configStore: TelegramConfigStore,
    ): TelegramProxyService = TelegramProxyService(context, configStore)

    @Provides
    @Singleton
    fun provideTelegramProxyCoordinator(
        proxyService: TelegramProxyService,
        tunnelController: TunnelController,
        configStore: TelegramConfigStore,
    ): TelegramProxyCoordinator = TelegramProxyCoordinator(proxyService, tunnelController, configStore)
}
