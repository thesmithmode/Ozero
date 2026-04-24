package ru.ozero.enginebyedpi

class ByeDpiProxy {

    init {
        System.loadLibrary("byedpi")
    }

    external fun jniStartProxy(args: Array<String>): Int

    external fun jniStopProxy(): Int

    external fun jniForceClose(): Int
}
