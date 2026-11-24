package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ru.ozero.engineopera.OperaEngine
import ru.ozero.engineopera.OperaProxyCommand
import ru.ozero.engineopera.OperaProxyRuntime
import ru.ozero.engineopera.SubprocessOperaProxyLauncher
import ru.ozero.enginescore.EnginePlugin
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OperaModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideOperaEngine(@ApplicationContext context: Context): EnginePlugin {
        val binary = File(context.applicationInfo.nativeLibraryDir, OperaProxyCommand.BINARY_NAME)
        val runtime = OperaProxyRuntime(
            SubprocessOperaProxyLauncher(binary, File(context.cacheDir, "opera-proxy")),
        )
        return OperaEngine(runtime) { binary.isFile && binary.canExecute() }
    }
}
