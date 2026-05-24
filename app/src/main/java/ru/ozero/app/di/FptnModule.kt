package ru.ozero.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginefptn.DataStoreFptnConfigStore
import ru.ozero.enginefptn.FptnConfigStore
import ru.ozero.enginefptn.FptnEngine
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FptnPrefs

@Module
@InstallIn(SingletonComponent::class)
object FptnModule {

    @Provides
    @Singleton
    @FptnPrefs
    fun provideFptnDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("fptn_prefs") },
        )

    @Provides
    @Singleton
    fun provideFptnConfigStore(
        @FptnPrefs dataStore: DataStore<Preferences>,
    ): FptnConfigStore = DataStoreFptnConfigStore(dataStore)

    @Provides
    @Singleton
    @IntoSet
    fun provideFptnEngine(
        store: FptnConfigStore,
        tunnelController: TunnelController,
    ): EnginePlugin = FptnEngine(
        configStore = store,
        onEngineFailed = { reason -> tunnelController.onEngineDied(EngineId.FPTN, reason) },
    )
}
