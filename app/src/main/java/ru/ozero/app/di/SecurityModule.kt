package ru.ozero.app.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.security.SecurityGuard
import ru.ozero.security.SecurityWatchdog
import javax.inject.Singleton
import kotlin.system.exitProcess

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityGuard(): SecurityGuard = SecurityGuard()

    @Provides
    @Singleton
    fun provideSecurityWatchdog(guard: SecurityGuard): SecurityWatchdog =
        SecurityWatchdog(
            guard = guard,
            onCompromised = { reasons ->
                Log.e(TAG, "compromised: $reasons — завершаем")
                exitProcess(1)
            },
        )

    private const val TAG = "SecurityModule"
}
