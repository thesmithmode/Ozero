package ru.ozero.enginefptn

interface FptnHttpsClient {
    fun nativeCreate(
        host: String,
        port: Int,
        sni: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    fun nativeDestroy(handle: Long)
    fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse
    fun nativePost(handle: Long, path: String, body: String, timeoutSeconds: Int): FptnNativeResponse
}

class FptnNativeHttpsClient : FptnHttpsClient {

    external override fun nativeCreate(
        host: String,
        port: Int,
        sni: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    external override fun nativeDestroy(handle: Long)

    external override fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse

    external override fun nativePost(
        handle: Long,
        path: String,
        body: String,
        timeoutSeconds: Int,
    ): FptnNativeResponse
}
