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
import ru.ozero.enginewarp.CloudflareWarpAutoConfig
import ru.ozero.enginewarp.DataStoreWarpConfigStore
import ru.ozero.enginewarp.EngineWarp
import ru.ozero.enginewarp.HttpClient
import ru.ozero.enginewarp.HttpUrlConnectionClient
import ru.ozero.enginewarp.StubWarpSdkBridge
import ru.ozero.enginewarp.StubWireguardKeyPairGenerator
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfigStore
import ru.ozero.enginewarp.WarpSdkBridge
import ru.ozero.enginewarp.WireguardKeyPairGenerator
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
    fun provideWarpConfigStore(
        @WarpPrefs dataStore: DataStore<Preferences>,
    ): WarpConfigStore = DataStoreWarpConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideWarpHttpClient(): HttpClient = HttpUrlConnectionClient()

    @Provides
    @Singleton
    fun provideWireguardKeyPairGenerator(): WireguardKeyPairGenerator =
        StubWireguardKeyPairGenerator()

    @Provides
    @Singleton
    fun provideWarpAutoConfig(
        httpClient: HttpClient,
        keypairGen: WireguardKeyPairGenerator,
    ): WarpAutoConfig = CloudflareWarpAutoConfig(httpClient, keypairGen)

    @Provides
    @Singleton
    fun provideWarpSdkBridge(): WarpSdkBridge = StubWarpSdkBridge()

    // Switch to real wireguard-android tunnel binding (com.wireguard.android:tunnel
    // через Maven Central, добавлен в engine-warp/build.gradle.kts). Активировать
    // удалив provideWarpSdkBridge() выше и раскомментив ниже:
    //
    // @Provides
    // @Singleton
    // fun provideWarpSdkBridge(
    //     @ApplicationContext context: Context,
    // ): WarpSdkBridge = RealWarpSdkBridge(context)
    //
    // RealWarpSdkBridge владеет GoBackend(context) — один процесс = один backend.
    // Hilt-scope @Singleton гарантирует это автоматически. См. README engine-warp.

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineWarp(
        autoConfig: WarpAutoConfig,
        store: WarpConfigStore,
        bridge: WarpSdkBridge,
    ): EnginePlugin = EngineWarp(autoConfig, store, bridge)
}
