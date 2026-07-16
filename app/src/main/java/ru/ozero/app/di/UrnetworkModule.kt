package ru.ozero.app.di

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
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
import ru.ozero.app.relay.RelayLockManager
import ru.ozero.app.relay.RelayNetworkMonitor
import ru.ozero.app.relay.UrnetworkRelayCoordinator
import ru.ozero.app.urnetwork.RealUrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.commonvpn.RuntimeFailureRouter
import ru.ozero.commonvpn.TunnelController
import ru.ozero.engineurnetwork.DataStoreUrnetworkConfigStore
import ru.ozero.engineurnetwork.EngineUrnetwork
import ru.ozero.engineurnetwork.RealUrnetworkJwtBootstrapper
import ru.ozero.engineurnetwork.RealUrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkContractStatusObserver
import ru.ozero.engineurnetwork.UrnetworkJwtBootstrapper
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.auth.RealUrnetworkAuthService
import ru.ozero.engineurnetwork.auth.RealUrnetworkDeviceIdentity
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkDeviceIdentity
import ru.ozero.enginescore.EngineId
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
        runtimeFailureRouter: RuntimeFailureRouter,
    ): UrnetworkSdkBridge = RealUrnetworkSdkBridge(
        app = context.applicationContext as Application,
        appVersion = ru.ozero.app.BuildConfig.VERSION_NAME,
        onIoLoopDied = { reason ->
            runtimeFailureRouter.handleEngineFailure(EngineId.URNETWORK, reason)
        },
    )

    @Provides
    @Singleton
    fun provideUrnetworkJwtBootstrapper(
        store: UrnetworkConfigStore,
        authService: UrnetworkAuthService,
        deviceIdentity: UrnetworkDeviceIdentity,
    ): UrnetworkJwtBootstrapper = RealUrnetworkJwtBootstrapper(
        configStore = store,
        authService = authService,
        deviceIdentity = deviceIdentity,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineUrnetwork(
        store: UrnetworkConfigStore,
        bridge: UrnetworkSdkBridge,
        jwtBootstrapper: UrnetworkJwtBootstrapper,
    ): EnginePlugin = EngineUrnetwork(
        configStore = store,
        sdkBridge = bridge,
        jwtBootstrapper = jwtBootstrapper,
    )

    @Provides
    @Singleton
    fun provideUrnetworkAuthService(
        @ApplicationContext context: Context,
    ): UrnetworkAuthService = RealUrnetworkAuthService(context.applicationContext as Application)

    @Provides
    @Singleton
    fun provideUrnetworkDeviceIdentity(
        @ApplicationContext context: Context,
    ): UrnetworkDeviceIdentity = RealUrnetworkDeviceIdentity(context.applicationContext as Application)

    @Provides
    @Singleton
    fun provideRelayNetworkMonitor(
        @ApplicationContext context: Context,
        bridge: UrnetworkSdkBridge,
    ): RelayNetworkMonitor = RelayNetworkMonitor(
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
        bridge = bridge,
    )

    @Provides
    @Singleton
    fun provideRelayLockManager(
        @ApplicationContext context: Context,
    ): RelayLockManager = RelayLockManager(
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager,
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager,
    )

    @Provides
    @Singleton
    fun provideUrnetworkRelayCoordinator(
        bridge: UrnetworkSdkBridge,
        configStore: UrnetworkConfigStore,
        tunnelController: TunnelController,
        jwtBootstrapper: UrnetworkJwtBootstrapper,
        networkMonitor: RelayNetworkMonitor,
        lockManager: RelayLockManager,
    ): UrnetworkRelayCoordinator = UrnetworkRelayCoordinator(
        bridge = bridge,
        configStore = configStore,
        tunnelController = tunnelController,
        jwtBootstrapper = jwtBootstrapper,
        networkMonitor = networkMonitor,
        relayLockManager = lockManager,
    )

    @Provides
    @Singleton
    fun provideUrnetworkBalanceCache(
        @ApplicationContext context: Context,
    ): ru.ozero.app.urnetwork.UrnetworkBalanceCache =
        ru.ozero.app.urnetwork.RealUrnetworkBalanceCache(context)

    @Provides
    @Singleton
    fun provideUrnetworkBalanceRepository(
        bridge: UrnetworkSdkBridge,
        cache: ru.ozero.app.urnetwork.UrnetworkBalanceCache,
    ): UrnetworkBalanceRepository = RealUrnetworkBalanceRepository(bridge = bridge, cache = cache)

    @Provides
    @Singleton
    fun provideUrnetworkSharedTrafficHistory(
        @ApplicationContext context: Context,
    ): ru.ozero.app.urnetwork.UrnetworkSharedTrafficHistory =
        ru.ozero.app.urnetwork.RealUrnetworkSharedTrafficHistory(context)

    @Provides
    @Singleton
    fun provideUrnetworkContractStatusObserver(
        @ApplicationContext context: Context,
        bridge: UrnetworkSdkBridge,
        tunnelController: TunnelController,
    ): UrnetworkContractStatusObserver = UrnetworkContractStatusObserver(
        bridge = bridge,
        tunnelController = tunnelController,
        requestStopVpn = { reason ->
            val intent = android.content.Intent(
                context,
                ru.ozero.commonvpn.OzeroVpnService::class.java,
            ).apply {
                action = ru.ozero.commonvpn.OzeroVpnService.ACTION_STOP
                putExtra("stop_reason", reason)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        },
    )
}
