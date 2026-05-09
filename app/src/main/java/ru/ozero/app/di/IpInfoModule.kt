package ru.ozero.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ip.DefaultVpnNetworkLocator
import ru.ozero.app.ip.VpnNetworkLocator
import ru.ozero.commonnet.IpInfoProvider
import ru.ozero.commonnet.OkHttpIpInfoProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IpInfoModule {

    @Provides
    @Singleton
    fun provideIpInfoProvider(): IpInfoProvider = OkHttpIpInfoProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnNetworkLocatorModule {
    @Binds
    @Singleton
    abstract fun bindVpnNetworkLocator(impl: DefaultVpnNetworkLocator): VpnNetworkLocator
}
