package ru.ozero.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.ozero.app.logging.BootDiagnostics
import ru.ozero.app.logging.BootFileLogger
import javax.inject.Inject

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            BootFileLogger.init(base)
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
        runCatching { BootFileLogger.info(TAG, "onCreate after super") }
    }

    private companion object {
        const val TAG = "OzeroApp"
    }
}
