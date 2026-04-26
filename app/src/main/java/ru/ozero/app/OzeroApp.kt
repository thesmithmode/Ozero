package ru.ozero.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import ru.ozero.app.data.CrashLogStore
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class OzeroApp : Application() {

    @Inject lateinit var crashLogStore: CrashLogStore

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
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
