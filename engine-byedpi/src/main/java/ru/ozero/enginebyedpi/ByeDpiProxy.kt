package ru.ozero.enginebyedpi

import android.util.Log

class ByeDpiProxy {

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int

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
                    Log.i(TAG, "libbyedpi loaded")
                } catch (e: UnsatisfiedLinkError) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                    Log.e(TAG, "loadLibrary failed: $loadError")
                } catch (e: SecurityException) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                    Log.e(TAG, "loadLibrary denied: $loadError")
                } catch (e: Throwable) {
                    loadError = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                    libraryLoaded = false
                    Log.e(TAG, "loadLibrary THROWN: $loadError")
                } finally {
                    loadAttempted = true
                }
            }
        }
    }
}
