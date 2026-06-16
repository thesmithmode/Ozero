package ru.ozero.app.di

import android.content.ComponentName
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.app.warp.WarpEngineService
import ru.ozero.commonvpn.RuntimeFailureRouter
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginewarp.DataStoreWarpConfigSlotStore
import ru.ozero.enginewarp.DataStoreWarpConfigStore
import ru.ozero.enginewarp.EngineWarp
import ru.ozero.enginewarp.HttpClient
import ru.ozero.enginewarp.HttpUrlConnectionClient
import ru.ozero.enginewarp.MirrorRanker
import ru.ozero.enginewarp.PrefsMirrorRanker
import ru.ozero.enginewarp.ProxyWarpAutoConfig
import ru.ozero.enginewarp.RemoteAwgRuntime
import ru.ozero.enginewarp.RealWarpSdkBridge
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpConfFileImporter
import ru.ozero.enginewarp.WarpEndpointProber
import ru.ozero.enginewarp.WarpFileImporter
import ru.ozero.enginewarp.WarpSdkBridge
import ru.ozero.enginewarp.runtimeFingerprint
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
    fun provideMirrorRanker(@ApplicationContext context: Context): MirrorRanker =
        PrefsMirrorRanker(
            context.getSharedPreferences("warp_mirror_ranks", Context.MODE_PRIVATE),
        )

    @Provides
    @Singleton
    fun provideWarpAutoConfig(
        httpClient: HttpClient,
        ranker: MirrorRanker,
    ): WarpAutoConfig = ProxyWarpAutoConfig(httpClient, ranker = ranker)

    @Provides
    @Singleton
    fun provideWarpConfFileImporter(): WarpFileImporter = WarpConfFileImporter()

    @Provides
    @Singleton
    fun provideWarpEndpointProber(): WarpEndpointProber = WarpEndpointProber()

    @Provides
    @Singleton
    fun provideWarpSdkBridge(
        @ApplicationContext context: Context,
        runtimeFailureRouter: RuntimeFailureRouter,
    ): WarpSdkBridge = RealWarpSdkBridge(
        RemoteAwgRuntime(
            context = context,
            serviceComponent = ComponentName(context, WarpEngineService::class.java),
            onProcessDied = {
                runtimeFailureRouter.handleEngineFailure(EngineId.WARP, "remote-binder-died")
            },
        ),
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineWarp(
        @ApplicationContext context: Context,
        autoConfig: WarpAutoConfig,
        store: WarpConfigSlotStore,
        bridge: WarpSdkBridge,
        settingsRepository: SettingsRepository,
        endpointProber: WarpEndpointProber,
    ): EnginePlugin = EngineWarp(
        autoConfig = autoConfig,
        configStore = store,
        sdkBridge = bridge,
        uapiPathProvider = { context.dataDir.absolutePath },
        context = context,
        socketProtector = ru.ozero.enginescore.VpnSocketProtectorHolder,
        ipv6EnabledProvider = {
            runBlocking {
                withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                    runCatching { settingsRepository.settings.first() }.getOrNull()
                }?.ipv6Enabled ?: SettingsModel.DEFAULT_IPV6_ENABLED
            }
        },
        endpointProber = endpointProber,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideWarpRuntimeConfigProvider(
        store: WarpConfigSlotStore,
    ): EngineRuntimeConfigProvider = object : EngineRuntimeConfigProvider {
        override val engineId: EngineId = EngineId.WARP
        override val changes = store.activeSlot().map { it?.runtimeFingerprint() }
        override val includeStarting: Boolean = false
        override val replayAfterStarting: Boolean = true
        override val restartReason: String = "WARP active slot changed while active -> restart"
    }

    private const val SETTINGS_READ_TIMEOUT_MS = 1_500L
}
