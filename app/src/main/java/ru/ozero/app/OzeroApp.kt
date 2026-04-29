package ru.ozero.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ozero.app.data.CrashLogStore
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.BootDiagnostics
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.logging.LogBuffer
import ru.ozero.security.SecurityWatchdog
import javax.inject.Inject

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var securityWatchdog: SecurityWatchdog

    @Inject lateinit var logBuffer: LogBuffer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            BootFileLogger.init(base)
            val crashStore = runCatching { CrashLogStore(base.filesDir) }.getOrNull()
            BootDiagnostics.installUncaughtHandler(
                crashSink = crashStore?.let { store -> { t, e -> store.write(t, e) } },
            )
            BootFileLogger.info(
                TAG,
                "attachBaseContext sdk=${Build.VERSION.SDK_INT} " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL}",
            )
            BootDiagnostics.dumpExitReasons(base)
        }
    }

    override fun onCreate() {
        runCatching { BootFileLogger.info(TAG, "onCreate before super") }
        super.onCreate()
        runCatching {
            AppLogger.attach(logBuffer)
            BootFileLogger.info(TAG, "onCreate after super")
            AppLogger.i(TAG, "app started pid=${android.os.Process.myPid()} sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER}/${Build.MODEL}")
        }.onFailure { BootFileLogger.error(TAG, "AppLogger.attach failed", it) }
        if (shouldStartSecurityWatchdog()) {
            runCatching { securityWatchdog.start(appScope) }
        } else {
            runCatching { BootFileLogger.info(TAG, "security watchdog skipped in debug/test runtime") }
        }
    }

    private fun shouldStartSecurityWatchdog(): Boolean =
        !BuildConfig.DEBUG && !isRobolectricRuntime()

    private fun isRobolectricRuntime(): Boolean =
        runCatching { Class.forName("org.robolectric.RuntimeEnvironment") }.isSuccess

    private companion object {
        const val TAG = "OzeroApp"
    }
}
