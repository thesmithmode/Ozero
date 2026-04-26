package ru.ozero.enginebyedpi

import android.util.Log

class ByeDpiProxy {

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int

    companion object {
        // Lazy load: если устройство не имеет нужный ABI (stripped APK / неподдерживаемая
        // архитектура), UnsatisfiedLinkError будет в момент создания ByeDpiProxy(), что
        // ломает DI-граф приложения. Грузим в init companion object — один раз на JVM,
        // и оборачиваем чтобы движок мог вернуть StartResult.Failure вместо краша.
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
