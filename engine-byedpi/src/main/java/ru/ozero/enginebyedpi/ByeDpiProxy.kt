package ru.ozero.enginebyedpi

import ru.ozero.enginescore.PersistentLoggers

interface ByeDpiProxyContract {
    fun startProxy(args: Array<String>): Int
    fun stopProxy(): Int
    fun forceClose(): Int
}

class ByeDpiProxy : ByeDpiProxyContract {

    private external fun jniStartProxy(args: Array<String>): Int

    private external fun jniStopProxy(): Int

    private external fun jniForceClose(): Int

    override fun startProxy(args: Array<String>): Int = jniStartProxy(args)

    override fun stopProxy(): Int = jniStopProxy()

    // SENTINEL [project_byedpi_native_guard_ownership]: forceClose() НЕ должен релизить g_proxy_running.
    // Native: только jniStartProxy владеет guard, jniForceClose триггерит graceful shutdown цикла main().
    override fun forceClose(): Int = jniForceClose()

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
                    PersistentLoggers.debug(TAG, "libbyedpi loaded")
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
