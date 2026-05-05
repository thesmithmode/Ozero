package ru.ozero.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.corebackup.AppBackupManager
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.enginewarp.WarpConfigSlotStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideAppBackupManager(
        ozeroSettings: DataStore<Preferences>,
        warpSlotStore: WarpConfigSlotStore,
        urnetworkStore: UrnetworkConfigStore,
        splitRuleDao: AppSplitRuleDao,
    ): AppBackupManager = AppBackupManager(ozeroSettings, warpSlotStore, urnetworkStore, splitRuleDao)
}
