package ru.ozero.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.ozero.app.data.CrashLogStore
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.subscription.HarvestWorker
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class OzeroApp : Application(), Configuration.Provider {

    @Inject lateinit var crashLogStore: CrashLogStore

    @Inject lateinit var logcatReader: LogcatReader

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        // In-memory ring buffer для UI-вкладки логов. Не пишет на диск, при
        // kill процесса очищается. Старт сразу — чтобы захватить ранние логи.
        logcatReader.start()
        // E16.1: запускаем periodic harvester. KEEP-policy → не пересоздаёт
        // существующий job при каждом старте, schedule сохраняется между
        // запусками приложения и перезагрузками.
        HarvestWorker.enqueueUnique(this)
    }

    /**
     * RT.11.1: подменяем uncaught handler. Логируем в локальный store,
     * затем делегируем дальше — system-default покажет ANR-диалог
     * и убьёт процесс. Никаких сетевых запросов, никаких внешних SDK.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashLogStore.write(thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "crashLog write failed", t)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                exitProcess(EXIT_CRASH)
            }
        }
    }

    private companion object {
        const val TAG = "OzeroApp"
        const val EXIT_CRASH = 2
    }
}
