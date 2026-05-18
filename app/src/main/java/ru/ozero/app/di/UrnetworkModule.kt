package ru.ozero.app.di

import android.app.Application
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
import ru.ozero.app.relay.UrnetworkRelayCoordinator
import ru.ozero.app.urnetwork.RealUrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.commonvpn.TunnelController
import ru.ozero.engineurnetwork.DataStoreUrnetworkConfigStore
import ru.ozero.engineurnetwork.EngineUrnetwork
import ru.ozero.engineurnetwork.RealUrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.auth.RealUrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UrnetworkPrefs

@Module
@InstallIn(SingletonComponent::class)
object UrnetworkModule {

    @Provides
    @Singleton
    @UrnetworkPrefs
    fun provideUrnetworkDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("urnetwork_prefs") },
        )

    @Provides
    @Singleton
    fun provideUrnetworkConfigStore(
        @UrnetworkPrefs dataStore: DataStore<Preferences>,
    ): UrnetworkConfigStore = DataStoreUrnetworkConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideUrnetworkSdkBridge(
        @ApplicationContext context: Context,
    ): UrnetworkSdkBridge = RealUrnetworkSdkBridge(
        app = context.applicationContext as Application,
        appVersion = ru.ozero.app.BuildConfig.VERSION_NAME,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineUrnetwork(
        store: UrnetworkConfigStore,
        bridge: UrnetworkSdkBridge,
        authService: UrnetworkAuthService,
    ): EnginePlugin = EngineUrnetwork(store, bridge, authService)

    @Provides
    @Singleton
    fun provideUrnetworkAuthService(
        @ApplicationContext context: Context,
    ): UrnetworkAuthService = RealUrnetworkAuthService(context.applicationContext as Application)

    @Provides
    @Singleton
    fun provideUrnetworkRelayCoordinator(
        bridge: UrnetworkSdkBridge,
        configStore: UrnetworkConfigStore,
        tunnelController: TunnelController,
    ): UrnetworkRelayCoordinator = UrnetworkRelayCoordinator(bridge, configStore, tunnelController)

    @Provides
    @Singleton
    fun provideUrnetworkBalanceRepository(
        bridge: UrnetworkSdkBridge,
    ): UrnetworkBalanceRepository = RealUrnetworkBalanceRepository(
        bridge = bridge,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
}
