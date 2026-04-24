package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.enginebyedpi.ByeDpiEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    @Provides
    @Singleton
    fun provideEngines(): Map<EngineId, Engine> = mapOf(EngineId.BYEDPI to ByeDpiEngine())
}
