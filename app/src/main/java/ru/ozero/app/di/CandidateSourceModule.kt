package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ru.ozero.coreorchestrator.CandidateSource
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.engineamnezia.strategy.AwgCandidateSource
import ru.ozero.enginehysteria2.strategy.Hy2CandidateSource
import ru.ozero.enginenaive.strategy.NaiveCandidateSource
import ru.ozero.enginexray.strategy.XrayCandidateSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CandidateSourceModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideXrayCandidateSource(serverDao: ServerDao): CandidateSource =
        XrayCandidateSource(serverDao)

    @Provides
    @Singleton
    @IntoSet
    fun provideAwgCandidateSource(serverDao: ServerDao): CandidateSource =
        AwgCandidateSource(serverDao)

    @Provides
    @Singleton
    @IntoSet
    fun provideNaiveCandidateSource(serverDao: ServerDao): CandidateSource =
        NaiveCandidateSource(serverDao)

    @Provides
    @Singleton
    @IntoSet
    fun provideHy2CandidateSource(serverDao: ServerDao): CandidateSource =
        Hy2CandidateSource(serverDao)
}
