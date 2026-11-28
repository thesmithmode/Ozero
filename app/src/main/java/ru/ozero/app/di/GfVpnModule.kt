package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import ru.ozero.enginegfvpn.GfVpnEngine
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.settings.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object GfVpnModule {
    @Provides
    @IntoSet
    @Singleton
    fun provideEngine(
        @ApplicationContext context: Context,
        repository: SettingsRepository,
    ): EnginePlugin = GfVpnEngine(context, repository)
}
