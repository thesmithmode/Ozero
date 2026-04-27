package ru.ozero.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import ru.ozero.app.di.stubs.StubLibAwgDelegate
import ru.ozero.app.di.stubs.StubLibHy2Delegate
import ru.ozero.app.di.stubs.StubLibNaiveDelegate
import ru.ozero.app.di.stubs.StubLibTorDelegate
import ru.ozero.app.di.stubs.StubLibXrayDelegate
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.engineamnezia.AwgEngine
import ru.ozero.engineamnezia.LibAwgDelegate
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginebyedpi.ByeDpiProxy
import ru.ozero.enginehysteria2.Hy2Engine
import ru.ozero.enginehysteria2.LibHy2Delegate
import ru.ozero.enginenaive.LibNaiveDelegate
import ru.ozero.enginenaive.NaiveEngine
import ru.ozero.enginetor.LibTorDelegate
import ru.ozero.enginetor.TorEngine
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import ru.ozero.enginexray.LibXrayDelegate
import ru.ozero.enginexray.XrayEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideByedpiProxy(): ByeDpiProxy = ByeDpiProxy()

    @Provides
    @Singleton
    fun provideXrayDelegate(): LibXrayDelegate = StubLibXrayDelegate()

    @Provides
    @Singleton
    fun provideAwgDelegate(): LibAwgDelegate = StubLibAwgDelegate()

    @Provides
    @Singleton
    fun provideHy2Delegate(): LibHy2Delegate = StubLibHy2Delegate()

    @Provides
    @Singleton
    fun provideNaiveDelegate(): LibNaiveDelegate = StubLibNaiveDelegate()

    @Provides
    @Singleton
    fun provideTorDelegate(): LibTorDelegate = StubLibTorDelegate()

    @Provides
    @IntoMap
    @EngineKey(EngineId.BYEDPI)
    fun provideByedpiEngine(proxy: ByeDpiProxy): Engine = ByeDpiEngine(proxy)

    @Provides
    @IntoMap
    @EngineKey(EngineId.XRAY)
    fun provideXrayEngine(delegate: LibXrayDelegate): Engine = XrayEngine(delegate)

    @Provides
    @IntoMap
    @EngineKey(EngineId.AMNEZIA)
    fun provideAwgEngine(delegate: LibAwgDelegate): Engine = AwgEngine(delegate)

    @Provides
    @IntoMap
    @EngineKey(EngineId.HYSTERIA2)
    fun provideHy2Engine(delegate: LibHy2Delegate): Engine = Hy2Engine(delegate)

    @Provides
    @IntoMap
    @EngineKey(EngineId.NAIVE)
    fun provideNaiveEngine(delegate: LibNaiveDelegate): Engine = NaiveEngine(delegate)

    @Provides
    @IntoMap
    @EngineKey(EngineId.TOR)
    fun provideTorEngine(
        @ApplicationContext context: android.content.Context,
        delegate: LibTorDelegate,
        installer: DynamicTorInstaller,
    ): Engine = TorEngine(
        delegate = delegate,
        installer = installer,
        buildOptions = ru.ozero.enginetor.config.TorBuildOptions(
                        dataDir = java.io.File(context.filesDir, "tor").absolutePath,
        ),
    )
}
