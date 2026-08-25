package ru.ozero.app.soak

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoakTestEntryPoint {
    fun settingsRepository(): SettingsRepository

    @SingboxPrefs
    fun singboxDataStore(): DataStore<Preferences>

    fun subscriptionGroupDao(): SubscriptionGroupDao
    fun proxyProfileDao(): ProxyProfileDao
    fun tunnelController(): TunnelController
}
