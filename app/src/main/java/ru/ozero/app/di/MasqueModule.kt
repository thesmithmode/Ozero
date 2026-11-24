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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import ru.ozero.enginemasque.MasqueEngine
import ru.ozero.enginemasque.MasqueProfileStore
import ru.ozero.enginemasque.MasqueRuntime
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MasquePrefs

@Module
@InstallIn(SingletonComponent::class)
object MasqueModule {
    @Provides
    @Singleton
    @MasquePrefs
    fun provideMasqueDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("masque_prefs") },
        )

    @Provides
    @Singleton
    fun provideMasqueStore(@MasquePrefs store: DataStore<Preferences>): MasqueProfileStore = MasqueProfileStore(store)

    @Provides
    @Singleton
    fun provideMasqueRuntime(@ApplicationContext context: Context): MasqueRuntime {
        val directory = File(context.noBackupFilesDir, "masque-runtime")
        directory.mkdirs()
        directory.listFiles()?.filter { it.name.startsWith("masque-") }?.forEach { it.deleteRecursively() }
        return MasqueRuntime(binary(context), directory)
    }

    @Provides
    @Singleton
    @IntoSet
    fun provideMasqueEngine(
        @ApplicationContext context: Context,
        runtime: MasqueRuntime,
        store: MasqueProfileStore,
    ): EnginePlugin = MasqueEngine(
        runtime,
        store,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        available = { binary(context).canExecute() },
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideMasqueRuntimeConfigProvider(store: MasqueProfileStore): EngineRuntimeConfigProvider =
        object : EngineRuntimeConfigProvider {
            override val engineId = EngineId.MASQUE
            override val changes = store.settings().map { it.fingerprint() }.catch { emit(null) }
            override val restartReason = "MASQUE configuration changed"
        }

    private fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libusque.so")
}
