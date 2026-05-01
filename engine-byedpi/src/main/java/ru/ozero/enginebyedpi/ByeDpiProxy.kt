package ru.ozero.enginebyedpi

import ru.ozero.enginescore.PersistentLoggers

open class ByeDpiProxy {

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int

    open fun startProxy(args: Array<String>): Int = jniStartProxy(args)

    open fun stopProxy(): Int = jniStopProxy()

    open fun forceClose(): Int = jniForceClose()

    companion object {
        private const val TAG = "ByeDpiProxy"

        @JvmStatic
        @Volatile
        var libraryLoaded: Boolean = false
            private set

        @JvmStatic
        @Volatile
        var loadError: String? = null
            private set

        @Volatile
        private var loadAttempted: Boolean = false
        private val loadLock = Any()

        @JvmStatic
        fun loadOnce() {
            if (loadAttempted) return
            synchronized(loadLock) {
                if (loadAttempted) return
                try {
                    System.loadLibrary("byedpi")
                    libraryLoaded = true
                    PersistentLoggers.info(TAG, "libbyedpi loaded")
                } catch (e: UnsatisfiedLinkError) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                    PersistentLoggers.error(TAG, "loadLibrary failed: $loadError", e)
                } catch (e: SecurityException) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                    PersistentLoggers.error(TAG, "loadLibrary denied: $loadError", e)
                } catch (e: Throwable) {
                    loadError = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                    libraryLoaded = false
                    PersistentLoggers.error(TAG, "loadLibrary THROWN: $loadError", e)
                } finally {
                    loadAttempted = true
                }
            }
        }
    }
}
