package ru.ozero.singboxcore

import android.os.Build
import ru.ozero.enginescore.PersistentLoggers

object Libsingboxgojni {
    private const val TAG = "LibboxLoader"

    @Volatile
    var libraryLoaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    private var loadAttempted: Boolean = false
    private val loadLock = Any()

    fun loadOnce() {
        if (loadAttempted) return
        synchronized(loadLock) {
            if (loadAttempted) return
            loadAttempted = true
            val t0 = System.nanoTime()
            val ctx = "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
            PersistentLoggers.instance?.info(TAG, "loadLibrary begin $ctx")
            try {
                System.loadLibrary("box")
                libraryLoaded = true
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.info(TAG, "libbox loaded OK dt=${dtMs}ms")
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message ?: e.javaClass.simpleName
                libraryLoaded = false
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.error(TAG, "libbox load FAILED dt=${dtMs}ms: $loadError", e)
            } catch (e: SecurityException) {
                loadError = e.message ?: e.javaClass.simpleName
                libraryLoaded = false
                PersistentLoggers.instance?.error(TAG, "libbox load denied: $loadError", e)
            } catch (e: Throwable) {
                loadError = e.message ?: e.javaClass.simpleName
                libraryLoaded = false
                PersistentLoggers.instance?.error(TAG, "libbox load error: $loadError", e)
            }
        }
    }
}
