package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogExporter
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

    @Provides
    @Singleton
    fun provideLogExporter(@ApplicationContext context: Context): LogExporter = LogExporter(context)
}
