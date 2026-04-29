package ru.ozero.enginebyedpi

import android.util.Log
import ru.ozero.coreapi.PersistentLoggers

class ByeDpiProxy {

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int

    companion object {
        private const val TAG = "ByeDpiProxy"

        @JvmStatic
        var libraryLoaded: Boolean = false
            private set

        @JvmStatic
        var loadError: String? = null
            private set

        init {
            try {
                System.loadLibrary("byedpi")
                libraryLoaded = true
                PersistentLoggers.instance?.info(TAG, "libbyedpi loaded")
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message
                Log.e(TAG, "loadLibrary failed: ${e.message}")
                PersistentLoggers.instance?.error(TAG, "loadLibrary failed: ${e.message}", e)
                libraryLoaded = false
            }
        }
    }
}
