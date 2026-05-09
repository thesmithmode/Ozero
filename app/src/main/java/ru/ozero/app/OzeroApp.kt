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
import kotlinx.coroutines.launch
import ru.ozero.app.data.CrashLogStore
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.logging.BootDiagnostics
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.ui.onboarding.FirstRunBootstrap
import ru.ozero.app.ui.splittunnel.AppListProvider
import ru.ozero.engineurnetwork.UrnetworkRuntime
import javax.inject.Inject

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var logBuffer: LogBuffer

    @Inject lateinit var firstRunBootstrap: FirstRunBootstrap

    @Inject lateinit var appListProvider: AppListProvider

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
        super.onCreate()
        runCatching { System.loadLibrary("am-go") }
            .onFailure { BootFileLogger.error(TAG, "am-go eager load failed", it) }
        runCatching { System.loadLibrary("gojni") }
            .onFailure { BootFileLogger.error(TAG, "gojni eager load failed", it) }
        runCatching {
            AppLogger.attach(logBuffer)
        }.onFailure { BootFileLogger.error(TAG, "AppLogger.attach failed", it) }
        appScope.launch {
            runCatching { UrnetworkRuntime.ensure(this@OzeroApp) }
                .onFailure { BootFileLogger.error(TAG, "UrnetworkRuntime.ensure failed", it) }
        }
        appScope.launch {
            runCatching { firstRunBootstrap.runIfFirstStart() }
                .onFailure { BootFileLogger.warn(TAG, "firstRunBootstrap.runIfFirstStart failed", it) }
        }
        appScope.launch {
            runCatching { appListProvider.loadApps() }
                .onFailure { BootFileLogger.warn(TAG, "appListProvider prewarm failed", it) }
        }
    }

    private companion object {
        const val TAG = "OzeroApp"
    }
}
