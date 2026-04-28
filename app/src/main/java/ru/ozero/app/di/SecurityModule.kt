package ru.ozero.app.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.ozero.security.SecurityGuard
import ru.ozero.security.SecurityStateHolder
import ru.ozero.security.SecurityWatchdog
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityGuard(): SecurityGuard = SecurityGuard()

    @Provides
    @Singleton
    fun provideSecurityWatchdog(guard: SecurityGuard): SecurityWatchdog {
        lateinit var watchdog: SecurityWatchdog
        watchdog = SecurityWatchdog(
            guard = guard,
            onCompromised = { reasons ->
                Log.e(TAG, "compromised env detected: $reasons — VPN disabled")
                SecurityStateHolder.signal(reasons)
                watchdog.stop()
            },
        )
        return watchdog
    }

    private const val TAG = "SecurityModule"
}
