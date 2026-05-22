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
import ru.ozero.enginemasterdns.DataStoreMasterDnsConfigStore
import ru.ozero.enginemasterdns.MasterDnsClientService
import ru.ozero.enginemasterdns.MasterDnsClientWrapper
import ru.ozero.enginemasterdns.MasterDnsConfigStore
import ru.ozero.enginemasterdns.MasterDnsConfigWriter
import ru.ozero.enginemasterdns.MasterDnsEngine
import ru.ozero.enginemasterdns.MasterDnsPortAllocator
import ru.ozero.enginemasterdns.MasterDnsResolversCache
import ru.ozero.enginescore.EnginePlugin
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MasterDnsPrefs

@Module
@InstallIn(SingletonComponent::class)
object MasterDnsModule {

    @Provides
    @Singleton
    @MasterDnsPrefs
    fun provideMasterDnsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("masterdns_prefs") },
        )

    @Provides
    @Singleton
    fun provideMasterDnsConfigStore(
        @MasterDnsPrefs dataStore: DataStore<Preferences>,
    ): MasterDnsConfigStore = DataStoreMasterDnsConfigStore(dataStore)

    @Provides
    @Singleton
    fun provideMasterDnsClientService(
        @ApplicationContext context: Context,
    ): MasterDnsClientService {
        val workDir = File(context.filesDir, "masterdns")
        return MasterDnsClientService(
            workDirProvider = { workDir },
            wrapperFactory = { MasterDnsClientWrapper(context.applicationInfo.nativeLibraryDir) },
            writer = MasterDnsConfigWriter(workDir),
        )
    }

    @Provides
    @Singleton
    fun provideMasterDnsResolversCache(
        configStore: MasterDnsConfigStore,
    ): MasterDnsResolversCache = MasterDnsResolversCache(
        config = configStore.config(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideMasterDnsEngine(
        service: MasterDnsClientService,
        resolversCache: MasterDnsResolversCache,
    ): EnginePlugin = MasterDnsEngine(
        serviceFactory = { service },
        portAllocator = MasterDnsPortAllocator(),
        resolversProvider = { resolversCache.snapshot() },
        configTomlProvider = { resolversCache.configTomlSnapshot() },
    )
}
