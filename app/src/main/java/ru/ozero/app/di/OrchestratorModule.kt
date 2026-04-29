package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.coreapi.ByeDpiArgsSource
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.CandidateSource
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
    fun provideByeDpiArgsSource(repo: SettingsRepository): ByeDpiArgsSource =
        ByeDpiArgsSource {
            repo.settings.map { it.byedpiWinningArgs }.firstOrNull()
        }

    @Provides
    @Singleton
    fun provideStrategyEngine(
        engines: Map<EngineId, @JvmSuppressWildcards Engine>,
        candidateSources: Set<@JvmSuppressWildcards CandidateSource>,
        byedpiArgsSource: ByeDpiArgsSource,
    ): StrategyEngine = StrategyEngine(
        engines = engines,
        extraSources = candidateSources.toList(),
        byedpiArgsSource = byedpiArgsSource,
    )
}
