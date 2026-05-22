package ru.ozero.enginefptn

class FptnNativeSniChecker {

    external fun nativeCreate(
        host: String,
        port: Int,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    external fun nativeCheckSni(handle: Long, sni: String): Boolean

    external fun nativeDestroy(handle: Long)
}
