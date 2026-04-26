package ru.ozero.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ozero.app.ui.splittunnel.AppListProvider
import ru.ozero.app.ui.splittunnel.DefaultAppListProvider
import ru.ozero.corestorage.OzeroDatabase
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.dao.ConnectionLogDao
import ru.ozero.corestorage.dao.ServerDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideOzeroDatabase(
        @ApplicationContext context: Context,
    ): OzeroDatabase = OzeroDatabase.create(context)

    @Provides
    fun provideServerDao(database: OzeroDatabase): ServerDao = database.serverDao()

    @Provides
    fun provideConnectionLogDao(database: OzeroDatabase): ConnectionLogDao = database.connectionLogDao()

    @Provides
    fun provideAppSplitRuleDao(database: OzeroDatabase): AppSplitRuleDao = database.appSplitRuleDao()
}

@Module
@dagger.hilt.InstallIn(SingletonComponent::class)
abstract class AppListProviderModule {

    @dagger.Binds
    @Singleton
    abstract fun bindAppListProvider(impl: DefaultAppListProvider): AppListProvider
}
