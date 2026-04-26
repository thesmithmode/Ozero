package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ui.diag.DefaultDiagnosticsEngine
import ru.ozero.app.ui.diag.DiagnosticsEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {

    @Provides
    @Singleton
    fun provideDiagnosticsEngine(): DiagnosticsEngine = DefaultDiagnosticsEngine()
}
