package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.di.stubs.StubDynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import javax.inject.Singleton

/**
 * Tor on-demand installer. Сейчас stub (бинари считаются уже доставленными).
 * RT.5 заменит реализацию на PlayCoreInstaller (SplitInstallManager) — без
 * изменений в TorEngine: оба возвращают InstallResult.
 *
 * Выделено в отдельный модуль чтобы android-test варианты могли подменить
 * @Provides на FakeInstaller через @TestInstallIn без трогания EngineModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object TorModule {

    @Provides
    @Singleton
    fun provideDynamicTorInstaller(): DynamicTorInstaller = StubDynamicTorInstaller()
}
