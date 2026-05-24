package ru.ozero.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.singboxroom.SingboxDatabase
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SingboxModule {

    @Provides
    @Singleton
    @SingboxPrefs
    fun provideSingboxDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("singbox_prefs") },
        )

    @Provides
    @Singleton
    fun provideSingboxDatabase(
        @ApplicationContext context: Context,
    ): SingboxDatabase =
        Room.databaseBuilder(context, SingboxDatabase::class.java, "singbox.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideSubscriptionGroupDao(db: SingboxDatabase): SubscriptionGroupDao =
        db.subscriptionGroupDao()

    @Provides
    @Singleton
    fun provideProxyProfileDao(db: SingboxDatabase): ProxyProfileDao =
        db.proxyProfileDao()

    @Provides
    @Singleton
    fun provideGroupSeeder(groupDao: SubscriptionGroupDao): GroupSeeder =
        GroupSeeder(groupDao)

    @Provides
    @Singleton
    fun provideRawUpdater(
        groupDao: SubscriptionGroupDao,
        profileDao: ProxyProfileDao,
    ): RawUpdater =
        RawUpdater(
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
            groupDao = groupDao,
            profileDao = profileDao,
        )

    @Provides
    @Singleton
    @IntoSet
    fun provideSingboxEngine(
        @ApplicationContext context: Context,
        @SingboxPrefs dataStore: DataStore<Preferences>,
    ): EnginePlugin = SingboxEngine(context, dataStore)
}
