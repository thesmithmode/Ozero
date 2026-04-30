package ru.ozero.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.data.RoomSessionStatsRecorder
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.NativeHevTunnelGateway
import ru.ozero.commonvpn.SessionStatsRecorder
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EnginePlugin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VpnModule {

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
    fun provideChainOrchestrator(
        engines: Set<@JvmSuppressWildcards EnginePlugin>,
    ): ChainOrchestrator = ChainOrchestrator(engines = engines)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionStatsRecorderModule {

    @Binds
    @Singleton
    abstract fun bindSessionStatsRecorder(impl: RoomSessionStatsRecorder): SessionStatsRecorder
}
