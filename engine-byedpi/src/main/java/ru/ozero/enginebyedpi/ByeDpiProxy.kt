package ru.ozero.enginebyedpi

import android.util.Log

class ByeDpiProxy {

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int

    companion object {
        @JvmStatic
        var libraryLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("byedpi")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e("ByeDpiProxy", "loadLibrary failed: ${e.message}")
                libraryLoaded = false
            }
        }
    }
}
