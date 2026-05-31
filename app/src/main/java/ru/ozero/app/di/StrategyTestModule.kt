package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ui.strategy.AssetStrategyAssetSource
import ru.ozero.app.ui.strategy.DomainList
import ru.ozero.app.ui.strategy.DomainListManager
import ru.ozero.app.ui.strategy.DomainListStore
import ru.ozero.app.ui.strategy.FileDomainListStore
import ru.ozero.app.ui.strategy.FileSavedStrategyStore
import ru.ozero.app.ui.strategy.FileStrategyResultsStore
import ru.ozero.app.ui.strategy.FileUsageHistoryStore
import ru.ozero.app.ui.strategy.SavedStrategyStore
import ru.ozero.app.ui.strategy.SharedPrefsStrategyTestSettingsStore
import ru.ozero.app.ui.strategy.StrategyAssetSource
import ru.ozero.app.ui.strategy.StrategyProbeClientFactory
import ru.ozero.app.ui.strategy.StrategyResultsStore
import ru.ozero.app.ui.strategy.StrategyTestSettingsStore
import ru.ozero.app.ui.strategy.UsageHistoryStore
import ru.ozero.commonnet.AndroidNetworkProfileDetector
import ru.ozero.commonnet.NetworkProfileDetector
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.ByeDpiProxy
import ru.ozero.enginebyedpi.strategy.DefaultEvolutionResourcesProvider
import ru.ozero.enginebyedpi.strategy.EvolutionResourcesProvider
import ru.ozero.enginebyedpi.strategy.HttpSocksProbeClient
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StrategyTestEngine

@Module
@InstallIn(SingletonComponent::class)
object StrategyTestModule {

    @Provides
    @Singleton
    @StrategyTestEngine
    fun provideStrategyTestEnginePlugin(): EnginePlugin = ByeDpiEngine(ByeDpiProxy())

    @Provides
    @Singleton
    fun provideStrategyAssetSource(
        @ApplicationContext context: Context,
    ): StrategyAssetSource = AssetStrategyAssetSource(context)

    @Provides
    @Singleton
    fun provideStrategyResultsStore(
        @ApplicationContext context: Context,
    ): StrategyResultsStore = FileStrategyResultsStore(context.filesDir)

    @Provides
    @Singleton
    fun provideStrategyTestSettingsStore(
        @ApplicationContext context: Context,
    ): StrategyTestSettingsStore = SharedPrefsStrategyTestSettingsStore(context)

    @Provides
    @Singleton
    fun provideDomainListStore(
        @ApplicationContext context: Context,
    ): DomainListStore = FileDomainListStore(context.filesDir)

    @Provides
    @Singleton
    fun provideDomainListManager(
        @ApplicationContext context: Context,
        store: DomainListStore,
    ): DomainListManager {
        val builtIns = DomainListManager.BUILT_IN_CONFIGS.map { (id, name, activeByDefault) ->
            val domains = runCatching {
                context.assets.open("proxytest_$id.sites").bufferedReader().use { r ->
                    r.readLines().map(String::trim).filter(String::isNotEmpty)
                }
            }.getOrDefault(emptyList())
            DomainList(id = id, name = name, domains = domains, isActive = activeByDefault, isBuiltIn = true)
        }
        return DomainListManager(store, builtIns)
    }

    @Provides
    @Singleton
    fun provideSavedStrategyStore(
        @ApplicationContext context: Context,
    ): SavedStrategyStore = FileSavedStrategyStore(context.filesDir)

    @Provides
    @Singleton
    fun provideUsageHistoryStore(
        @ApplicationContext context: Context,
    ): UsageHistoryStore = FileUsageHistoryStore(context.filesDir)

    @Provides
    @Singleton
    fun provideEvolutionResourcesProvider(
        @ApplicationContext context: Context,
    ): EvolutionResourcesProvider = DefaultEvolutionResourcesProvider(context.filesDir)

    @Provides
    @Singleton
    fun provideNetworkProfileDetector(
        @ApplicationContext context: Context,
    ): NetworkProfileDetector = AndroidNetworkProfileDetector(context)

    @Provides
    fun provideStrategyProbeClientFactory(): StrategyProbeClientFactory =
        StrategyProbeClientFactory { socksPort, timeoutMs ->
            HttpSocksProbeClient(proxyPort = socksPort, timeoutMs = timeoutMs)
        }
}
