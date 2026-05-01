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
import ru.ozero.engineurnetwork.DataStoreUrnetworkConfigStore
import ru.ozero.engineurnetwork.EngineUrnetwork
import ru.ozero.engineurnetwork.StubUrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
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
    fun provideUrnetworkSdkBridge(): UrnetworkSdkBridge = StubUrnetworkSdkBridge()

    // Switch to real urnetwork/userwireguard binding (engine-urnetwork/libs/userwireguard.aar
    // собирается scripts/build_wireguard_android.sh; URnetworkSdk.aar — tools/build-urnetwork-aar.sh).
    // Активировать когда оба AAR собраны и RealUrnetworkSdkBridge реализован:
    //
    // @Provides
    // @Singleton
    // fun provideUrnetworkSdkBridge(
    //     @ApplicationContext context: Context,
    // ): UrnetworkSdkBridge = RealUrnetworkSdkBridge(context)
    //
    // См. README engine-urnetwork — оба AAR (URnetworkSdk + userwireguard)
    // должны быть в engine-urnetwork/libs/ до включения switch.

    @Provides
    @Singleton
    @IntoSet
    fun provideEngineUrnetwork(
        store: UrnetworkConfigStore,
        bridge: UrnetworkSdkBridge,
    ): EnginePlugin = EngineUrnetwork(store, bridge)
}
