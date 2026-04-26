package ru.ozero.app.di

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.di.stubs.StubDynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.PlayCoreSplitInstallClient
import ru.ozero.enginetor.dynamicmod.SplitInstallClient
import javax.inject.Singleton

/**
 * Tor on-demand installer + SplitInstall клиент.
 *
 * - [DynamicTorInstaller] — пока stub (бинари считаются доставленными). RT.5
 *   заменит реализацию на PlayCoreInstaller — без изменений в TorEngine.
 * - [SplitInstallClient] — production-обёртка над PlayCore SplitInstallManager;
 *   используется UI слоем (SettingsViewModel) для запуска on-demand загрузки
 *   и подписки на прогресс.
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

    @Provides
    @Singleton
    fun provideSplitInstallClient(
        @ApplicationContext context: Context,
    ): SplitInstallClient = PlayCoreSplitInstallClient(SplitInstallManagerFactory.create(context))
}
