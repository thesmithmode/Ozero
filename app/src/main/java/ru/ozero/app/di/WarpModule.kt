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
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginewarp.DataStoreWarpConfigSlotStore
import ru.ozero.enginewarp.DataStoreWarpConfigStore
import ru.ozero.enginewarp.EngineWarp
import ru.ozero.enginewarp.HttpClient
import ru.ozero.enginewarp.HttpUrlConnectionClient
import ru.ozero.enginewarp.ProxyWarpAutoConfig
import ru.ozero.enginewarp.RealWarpSdkBridge
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfFileImporter
import ru.ozero.enginewarp.WarpFileImporter
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpSdkBridge
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WarpPrefs

@Module
@InstallIn(SingletonComponent::class)
object WarpModule {

    @Provides
    @Singleton
    @WarpPrefs
    fun provideWarpDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("warp_prefs") },
        )

    @Provides
    @Singleton
    fun provideWarpConfigLegacyStore(
        @WarpPrefs dataStore: DataStore<Preferences>,
    ): DataStoreWarpConfigStore = DataStoreWarpConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideWarpConfigSlotStore(
        @WarpPrefs dataStore: DataStore<Preferences>,
        legacyStore: DataStoreWarpConfigStore,
    ): WarpConfigSlotStore = DataStoreWarpConfigSlotStore(dataStore, legacyStore)

    @Provides
    @Singleton
    fun provideWarpHttpClient(): HttpClient = HttpUrlConnectionClient()

    @Provides
    @Singleton
    fun provideWarpAutoConfig(
        httpClient: HttpClient,
    ): WarpAutoConfig = ProxyWarpAutoConfig(httpClient)

    @Provides
    @Singleton
    fun provideWarpConfFileImporter(): WarpFileImporter = WarpConfFileImporter()

    @Provides
    @Singleton
    fun provideWarpSdkBridge(): WarpSdkBridge = RealWarpSdkBridge()

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineWarp(
        @ApplicationContext context: Context,
        autoConfig: WarpAutoConfig,
        store: WarpConfigSlotStore,
        bridge: WarpSdkBridge,
    ): EnginePlugin = EngineWarp(
        autoConfig = autoConfig,
        configStore = store,
        sdkBridge = bridge,
        uapiPathProvider = { context.dataDir.absolutePath },
    )
}
