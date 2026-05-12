package ru.ozero.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ui.strategy.DomainListStore
import ru.ozero.app.ui.strategy.SavedStrategyStore
import ru.ozero.app.ui.strategy.StrategyTestSettingsStore
import ru.ozero.corebackup.AppBackupManager
import ru.ozero.corebackup.StrategyBackupProvider
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.enginebyedpi.strategy.GeneMemory
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.enginewarp.WarpConfigSlotStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideStrategyBackupProvider(
        settingsStore: StrategyTestSettingsStore,
        domainListStore: DomainListStore,
        savedStrategyStore: SavedStrategyStore,
        geneMemory: GeneMemory,
    ): StrategyBackupProvider = StrategyBackupProviderImpl(settingsStore, domainListStore, savedStrategyStore, geneMemory)

    @Provides
    @Singleton
    fun provideAppBackupManager(
        ozeroSettings: DataStore<Preferences>,
        warpSlotStore: WarpConfigSlotStore,
        urnetworkStore: UrnetworkConfigStore,
        splitRuleDao: AppSplitRuleDao,
        strategyProvider: StrategyBackupProvider,
    ): AppBackupManager = AppBackupManager(ozeroSettings, warpSlotStore, urnetworkStore, splitRuleDao, strategyProvider)
}
