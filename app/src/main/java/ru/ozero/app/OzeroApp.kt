package ru.ozero.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.ozero.app.data.CrashLogStore
import ru.ozero.app.logging.BootDiagnostics
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.subscription.HarvestWorker
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var crashLogStore: CrashLogStore

    @Inject lateinit var logcatReader: LogcatReader

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder().setMinimumLoggingLevel(Log.INFO)
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            BootFileLogger.init(base)
            installCrashHandler()
            BootFileLogger.info(
                TAG,
                "attachBaseContext sdk=${Build.VERSION.SDK_INT} " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "device=${Build.MANUFACTURER}/${Build.MODEL} " +
                    "is64=${android.os.Process.is64Bit()}",
            )
            BootDiagnostics.guardUnit("nativeLibraryDir dump") {
                val nativeDir = base.applicationInfo.nativeLibraryDir
                val libs = File(nativeDir).listFiles()
                    ?.joinToString(",") { "${it.name}(${it.length()}b)" } ?: "null"
                BootFileLogger.info(TAG, "nativeLibraryDir=$nativeDir libs=[$libs]")
            }
            BootDiagnostics.dumpExitReasons(base)
        } catch (t: Throwable) {
            writeEarlyCrash(base, "attachBaseContext", t)
            throw t
        }
    }

    override fun onCreate() {
        BootFileLogger.info(TAG, "onCreate before super")
        try {
            super.onCreate()
        } catch (t: Throwable) {
            BootFileLogger.error(TAG, "super.onCreate threw", t)
            writeEarlyCrash(this, "super.onCreate", t)
            throw t
        }
        BootFileLogger.info(TAG, "onCreate after super")
        BootDiagnostics.guardUnit("TProxyService probe") {
            BootFileLogger.info(
                TAG,
                "TProxyService.libraryLoaded=${hev.TProxyService.libraryLoaded} " +
                    "loadError=${hev.TProxyService.loadError}",
            )
        }
        BootDiagnostics.guardUnit("logcatReader.start") { logcatReader.start() }
        BootDiagnostics.guardUnit("HarvestWorker.enqueueUnique") {
            HarvestWorker.enqueueUnique(this)
        }
        BootFileLogger.info(TAG, "onCreate done")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            BootFileLogger.error(TAG, "UNCAUGHT thread=${thread.name}", throwable)
            runCatching { writeEarlyCrash(this, "uncaught:${thread.name}", throwable) }
            try {
                if (::crashLogStore.isInitialized) {
                    crashLogStore.write(thread, throwable)
                }
            } catch (t: Throwable) {
                BootFileLogger.error(TAG, "crashLogStore.write failed", t)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                exitProcess(EXIT_CRASH)
            }
        }
    }

    private fun writeEarlyCrash(ctx: Context, where: String, t: Throwable) {
        runCatching {
            val dir = File(ctx.filesDir, "debug").apply { mkdirs() }
            val file = File(dir, "early-crash.txt")
            val sw = StringWriter()
            sw.append("ts=").append(System.currentTimeMillis().toString()).append('\n')
            sw.append("where=").append(where).append('\n')
            sw.append("sdk=").append(Build.VERSION.SDK_INT.toString()).append('\n')
            sw.append("abi=").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
            PrintWriter(sw).use { t.printStackTrace(it) }
            file.appendText(sw.toString() + "\n---\n")
        }
    }

    private companion object {
        const val TAG = "OzeroApp"
        const val EXIT_CRASH = 2
    }
}
