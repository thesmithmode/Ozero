package ru.ozero.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ozero.enginescore.settings.AutoStartGateway
import ru.ozero.app.settings.BootReceiverAutoStartGateway
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.app.settings.SettingsRepositoryImpl
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.settings.UserFlagsRepositoryImpl
import ru.ozero.app.ui.onboarding.FirstRunBootstrap
import ru.ozero.app.ui.onboarding.NoOpFirstRunBootstrap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(SettingsKeys.DATASTORE_NAME) },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAutoStartGateway(impl: BootReceiverAutoStartGateway): AutoStartGateway

    @Binds
    @Singleton
    abstract fun bindUserFlagsRepository(impl: UserFlagsRepositoryImpl): UserFlagsRepository

    @Binds
    @Singleton
    abstract fun bindFirstRunBootstrap(impl: NoOpFirstRunBootstrap): FirstRunBootstrap
}
