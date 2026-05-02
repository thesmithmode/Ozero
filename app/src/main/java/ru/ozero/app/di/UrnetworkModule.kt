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

    // TODO(v0.0.2-5): вернуть RealUrnetworkSdkBridge после rebuild AAR.
    // URnetworkSdk.aar и userwireguard.aar — два независимых gomobile bind проекта,
    // каждый содержит свою lib/*/libgojni.so + go.Seq.class. AGP merge перезаписывает
    // одну .so → JNI registration теряется → "No implementation found for Sdk._init()".
    // Fix: rebuild gomobile bind с обоими go-модулями в одном `gomobile bind ./sdk ./userwireguard`
    // → одна совместная libgojni.so. Out of scope для v0.0.2-4 — Go toolchain работа.
    @Provides
    @Singleton
    fun provideUrnetworkSdkBridge(
        @ApplicationContext context: Context,
    ): UrnetworkSdkBridge = StubUrnetworkSdkBridge()

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
    ): UrnetworkAuthService = RealUrnetworkAuthService(context)
}
