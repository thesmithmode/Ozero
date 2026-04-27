package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.NativeHevTunnelGateway
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.pipeline.VpnEnginePipeline
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.StrategyEngine
import javax.inject.Singleton

/**
 * Биндинги для [VpnEnginePipeline] и его зависимостей.
 *
 * [TunnelController] — Singleton: VPN-тоннель один на app. Compose-UI и
 * VpnService читают одну и ту же StateFlow.
 *
 * [HevTunnelGateway] биндится на [NativeHevTunnelGateway] — production-обёртка
 * над hev-socks5-tunnel JNI. AndroidTest-вариант сможет подменить через
 * @TestInstallIn → FakeHevTunnelGateway без зачёта реальной нативки.
 */
@Module
@InstallIn(SingletonComponent::class)
object VpnPipelineModule {

    @Provides
    @Singleton
    fun provideTunnelController(): TunnelController = TunnelController()

    @Provides
    @Singleton
    fun provideHevTunnelGateway(
        @ApplicationContext context: Context,
    ): HevTunnelGateway = NativeHevTunnelGateway(context)

    @Provides
    @Singleton
    fun provideVpnEnginePipeline(
        engines: Map<EngineId, @JvmSuppressWildcards Engine>,
        strategy: StrategyEngine,
        orchestrator: Orchestrator,
        tunnelController: TunnelController,
        gateway: HevTunnelGateway,
    ): VpnEnginePipeline = VpnEnginePipeline(
        engines = engines,
        strategy = strategy,
        orchestrator = orchestrator,
        tunnelController = tunnelController,
        tunnelGateway = gateway,
    )
}
