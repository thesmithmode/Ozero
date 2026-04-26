package ru.ozero.app.di

import android.content.Context
import android.os.Build
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.PlayCoreDynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.PlayCoreSplitInstallClient
import ru.ozero.enginetor.dynamicmod.Sha256TorBinaryVerifier
import ru.ozero.enginetor.dynamicmod.SplitInstallClient
import ru.ozero.enginetor.dynamicmod.TorBinaryChecksums
import ru.ozero.enginetor.dynamicmod.TorBinaryVerifier
import java.io.File
import javax.inject.Singleton

/**
 * Tor on-demand installer + SplitInstall клиент.
 *
 * - [DynamicTorInstaller] — PlayCore-реализация (RT.5.3). После доставки сверяет
 *   нативки с эталонными SHA-256; mismatch → deferredUninstall + Failed.
 * - [SplitInstallClient] — production-обёртка над PlayCore SplitInstallManager;
 *   используется UI-слоем (SettingsViewModel) для запуска on-demand загрузки
 *   и подписки на прогресс.
 *
 * Выделено в отдельный модуль чтобы android-test варианты могли подменить
 * @Provides на Fake* через @TestInstallIn без трогания EngineModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object TorModule {

    @Provides
    @Singleton
    fun provideTorBinaryVerifier(): TorBinaryVerifier =
        Sha256TorBinaryVerifier(TorBinaryChecksums.byAbi)

    @Provides
    @Singleton
    fun provideDynamicTorInstaller(
        client: SplitInstallClient,
        verifier: TorBinaryVerifier,
        @ApplicationContext context: Context,
    ): DynamicTorInstaller = PlayCoreDynamicTorInstaller(
        client = client,
        verifier = verifier,
        nativeLibDirProvider = { File(context.applicationInfo.nativeLibraryDir) },
        currentAbi = { Build.SUPPORTED_ABIS.firstOrNull().orEmpty() },
    )

    @Provides
    @Singleton
    fun provideSplitInstallClient(
        @ApplicationContext context: Context,
    ): SplitInstallClient = PlayCoreSplitInstallClient(SplitInstallManagerFactory.create(context))
}
