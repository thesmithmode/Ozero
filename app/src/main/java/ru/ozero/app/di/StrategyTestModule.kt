package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ui.strategy.AssetStrategyAssetSource
import ru.ozero.app.ui.strategy.FileStrategyResultsStore
import ru.ozero.app.ui.strategy.StrategyAssetSource
import ru.ozero.app.ui.strategy.StrategyProbeClientFactory
import ru.ozero.app.ui.strategy.StrategyResultsStore
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.strategy.HttpSocksProbeClient
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StrategyTestModule {

    @Provides
    @Singleton
    fun provideStrategyTestEnginePlugin(byeDpi: ByeDpiEngine): EnginePlugin = byeDpi

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
    fun provideStrategyProbeClientFactory(): StrategyProbeClientFactory =
        StrategyProbeClientFactory { socksPort -> HttpSocksProbeClient(proxyPort = socksPort) }
}
