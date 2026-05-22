package ru.ozero.enginefptn

class FptnNativeHttpsClient {

    external fun nativeCreate(
        host: String,
        port: Int,
        sni: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse

    external fun nativePost(
        handle: Long,
        path: String,
        body: String,
        timeoutSeconds: Int,
    ): FptnNativeResponse
}
