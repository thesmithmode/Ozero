package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.StrategyEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrchestratorModule {

    @Provides
    @Singleton
    fun provideOrchestrator(): Orchestrator = Orchestrator()

    @Provides
    @Singleton
    fun provideStrategyEngine(
        engines: Map<EngineId, @JvmSuppressWildcards Engine>,
    ): StrategyEngine = StrategyEngine(engines)
}
