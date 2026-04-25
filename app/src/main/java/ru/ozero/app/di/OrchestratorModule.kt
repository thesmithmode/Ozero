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

    /**
     * `@JvmSuppressWildcards` обязателен — без него Kotlin сгенерирует
     * Map<EngineId, ? extends Engine>, а Hilt мульти-биндинг ожидает
     * Map<EngineId, Engine>. Параметр не разрешится → InjectException.
     */
    @Provides
    @Singleton
    fun provideStrategyEngine(
        engines: Map<EngineId, @JvmSuppressWildcards Engine>,
    ): StrategyEngine = StrategyEngine(engines)
}
