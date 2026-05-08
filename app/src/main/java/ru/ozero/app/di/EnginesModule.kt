package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.ByeDpiProxy
import ru.ozero.enginebyedpi.ByeDpiProxyContract
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnginesModule {

    @Provides
    @Singleton
    fun provideByeDpiProxy(): ByeDpiProxyContract = ByeDpiProxy()

    @Provides
    @Singleton
    fun provideByeDpiEngineDirect(proxy: ByeDpiProxyContract): ByeDpiEngine = ByeDpiEngine(proxy)

    @Provides
    @Singleton
    @IntoSet
    fun provideByeDpiEngine(byeDpiEngine: ByeDpiEngine): EnginePlugin = byeDpiEngine
}
