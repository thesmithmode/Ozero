package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogcatReader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLogBuffer(): LogBuffer = LogBuffer()

    @Provides
    @Singleton
    fun provideLogcatReader(buffer: LogBuffer): LogcatReader = LogcatReader(buffer)
}
