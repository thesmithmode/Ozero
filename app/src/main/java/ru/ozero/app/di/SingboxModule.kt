package ru.ozero.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.app.vpn.singboxRuntimeFingerprint
import ru.ozero.commonvpn.RuntimeFailureRouter
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import ru.ozero.singboxroom.SingboxDatabase
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
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
            .addMigrations(SingboxDatabase.MIGRATION_1_2)
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
    fun provideProxyChainDao(db: SingboxDatabase): ProxyChainDao =
        db.proxyChainDao()

    @Provides
    @Singleton
    fun provideGroupSeeder(
        groupDao: SubscriptionGroupDao,
        @SingboxPrefs dataStore: DataStore<Preferences>,
    ): GroupSeeder = GroupSeeder(groupDao) { deletedProfileIds ->
        dataStore.edit { prefs ->
            val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
            if (selectedProfileId != null && selectedProfileId in deletedProfileIds) {
                prefs.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
                prefs.remove(SingboxProbeService.BEAN_KEY)
            }
        }
    }

    @Provides
    @Singleton
    fun provideRawUpdater(
        groupDao: SubscriptionGroupDao,
        profileDao: ProxyProfileDao,
    ): RawUpdater =
        RawUpdater(
            okHttpClient = OkHttpClient(),
            groupDao = groupDao,
            profileDao = profileDao,
            userCaOkHttpClient = SubscriptionTrustClientFactory.create(),
        )

    @Provides
    @Singleton
    @IntoSet
    fun provideSingboxEngine(
        @ApplicationContext context: Context,
        @SingboxPrefs dataStore: DataStore<Preferences>,
        profileDao: ProxyProfileDao,
        proxyChainDao: ProxyChainDao,
        runtimeFailureRouter: RuntimeFailureRouter,
    ): EnginePlugin = SingboxEngine(
        context = context,
        dataStore = dataStore,
        profileDao = profileDao,
        proxyChainDao = proxyChainDao,
        onProcessDied = { runtimeFailureRouter.handleEngineFailure(EngineId.SINGBOX, "binder-died") },
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideSingboxRuntimeConfigProvider(
        @SingboxPrefs dataStore: DataStore<Preferences>,
        profileDao: ProxyProfileDao,
        proxyChainDao: ProxyChainDao,
    ): EngineRuntimeConfigProvider = object : EngineRuntimeConfigProvider {
        override val engineId: EngineId = EngineId.SINGBOX
        override val changes = combine(
            dataStore.data,
            profileDao.getAutoCandidatesFlow(MAX_SINGBOX_RUNTIME_PROFILE_SCAN),
            proxyChainDao.getAllFlow(),
        ) { prefs, profiles, chainSteps ->
            singboxRuntimeFingerprint(
                prefs = prefs,
                profiles = profiles,
                chainSteps = chainSteps,
                resolveProfileById = profileDao::getById,
            )
        }
        override val includeStarting: Boolean = false
        override val replayAfterStarting: Boolean = true
        override val restartReason: String = "singbox profile changed while connected -> restart"
    }

    private const val MAX_SINGBOX_RUNTIME_PROFILE_SCAN = 2_000
}
